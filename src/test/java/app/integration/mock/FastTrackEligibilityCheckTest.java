package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock test for Claim Data Standardization:decision:validation.
 * Verifies fast-track eligibility logic and downstream actions.
 */
@ExtendWith(MockitoExtension.class)
public class FastTrackEligibilityCheckTest {

    @Mock
    private TaskService taskService;

    @Mock
    private QueueService queueService;

    @Mock
    private CoverageReviewService coverageReviewService;

    @Mock
    private SiuService siuService;

    @InjectMocks
    private DecisionValidationService decisionValidationService;

    @Test
    void validate_fast_track_eligibility_criteria() {
        // Arrange: Prepare inputs satisfying all fast-track conditions
        Map<String, Object> payload = Map.of(
            "policy_status", "active",
            "cause_of_loss", "wind",
            "severity_score", "low",
            "has_attorney", false,
            "has_public_adjuster", false,
            "has_aob", false,
            "documentation_sufficient", true,
            "prior_conflict", false
        );

        // Act: Execute validation and standardization
        ValidationOutcome outcome = decisionValidationService.validateAndStandardize(payload);

        // Assert: Verify expected results
        assertNotNull(outcome, "Outcome should not be null");

        // Expected: Initial claim type set to Fast-track claim
        assertEquals("Fast-track claim", outcome.getInitialClaimType(),
            "Initial claim type should be Fast-track claim");

        // Expected: Payload standardized with fast_track_eligible=true
        assertTrue(outcome.isFastTrackEligible(),
            "Payload should indicate fast_track_eligible=true");

        // Expected: Adjuster assignment task created
        verify(taskService, times(1)).createAdjusterAssignment(any(String.class));

        // Expected: Acknowledgment queued
        verify(queueService, times(1)).queueAcknowledgment(any(String.class));

        // Expected: No coverage review or SIU tasks generated
        verifyNoInteractions(coverageReviewService, siuService);
    }
}
