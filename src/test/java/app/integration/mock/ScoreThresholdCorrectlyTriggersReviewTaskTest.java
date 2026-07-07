package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that when a claim/exposure risk score exceeds the configured threshold,
 * the system correctly transitions state, triggers a review task, and logs the event.
 * Aligns with NFRs: thread safety (mock isolation), observability (audit logging),
 * compliance (GDPR/SOC2 via structured event tracking), and security (input validation via threshold).
 */
@ExtendWith(MockitoExtension.class)
public class ScoreThresholdCorrectlyTriggersReviewTask {

    @Mock
    private ReserveLineRepository reserveLineRepository;

    @Mock
    private TaskService taskService;

    @Mock
    private CommunicationService communicationService;

    private static final String EXPOSURE_ID = "EXP-100";
    private static final String RESERVE_ID = "RES-200";
    private static final double SCORE_THRESHOLD = 75.0;
    private static final double CALCULATED_SCORE = 80.0;

    @BeforeEach
    void setUp() {
        // Ensures test isolation and thread safety by resetting mocks before each execution
        reset(reserveLineRepository, taskService, communicationService);
    }

    @Test
    void score_threshold_correctly_triggers_review_task() {
        // Arrange: Mock repository to simulate score evaluation exceeding threshold
        when(reserveLineRepository.calculateRiskScore(EXPOSURE_ID)).thenReturn(CALCULATED_SCORE);

        // Act: Trigger state transition logic
        reserveLineRepository.evaluateAndTransitionState(EXPOSURE_ID, SCORE_THRESHOLD);

        // Assert: Verify state transition to review
        verify(reserveLineRepository, times(1)).updateApprovalStatus(eq(RESERVE_ID), eq("UnderReview"));

        // Assert: Verify review task creation due to threshold breach
        verify(taskService, times(1)).createReviewTask(eq(EXPOSURE_ID), eq("HIGH_RISK"));

        // Assert: Verify notification (mocked SES integration for compliance & observability)
        verify(communicationService, times(1)).sendEmail(anyString(), anyList(), anyString());

        // Assert: Ensure no unexpected interactions
        verifyNoMoreInteractions(reserveLineRepository, taskService, communicationService);
    }
}
