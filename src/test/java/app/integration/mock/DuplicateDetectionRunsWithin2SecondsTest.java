package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DuplicateDetectionStateTransitionTest {

    private static final long MAX_DURATION_MS = 2000;
    private static final String CLAIM_ID = "CLAIM-TEST-001";
    private static final String TARGET_STATE = "UNDER_REVIEW";

    @Mock
    private DuplicateDetectionRepository duplicateDetectionRepository;

    @Mock
    private StateTransitionRepository stateTransitionRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    private InsuredEngagementService insuredEngagementService;

    @BeforeEach
    void setUp() {
        insuredEngagementService = new InsuredEngagementService(
                duplicateDetectionRepository,
                stateTransitionRepository,
                emailNotificationService
        );
    }

    @Test
    void duplicate_detection_runs_within_2_seconds() {
        // Arrange: Mock external I/O (DynamoDB, SES, etc.) to isolate timing constraint
        when(duplicateDetectionRepository.findDuplicates(CLAIM_ID)).thenReturn(0);
        when(stateTransitionRepository.updateState(CLAIM_ID, TARGET_STATE)).thenReturn(true);
        doNothing().when(emailNotificationService).sendNotification(anyString(), anyString(), anyString());

        // Act: Measure execution time of the decision/state transition flow
        long startTime = System.currentTimeMillis();
        boolean success = insuredEngagementService.processClaim(CLAIM_ID);
        long elapsedMillis = System.currentTimeMillis() - startTime;

        // Assert: Verify timing constraint and expected side-effects
        assertTrue(elapsedMillis <= MAX_DURATION_MS,
                String.format("Duplicate detection and state transition must complete within %d ms. Actual: %d ms",
                        MAX_DURATION_MS, elapsedMillis));
        assertTrue(success);
        verify(duplicateDetectionRepository, times(1)).findDuplicates(CLAIM_ID);
        verify(stateTransitionRepository, times(1)).updateState(CLAIM_ID, TARGET_STATE);
        verify(emailNotificationService, times(1)).sendNotification(eq(CLAIM_ID), anyString(), anyString());
    }

    // Minimal interfaces representing external I/O contracts
    interface DuplicateDetectionRepository {
        int findDuplicates(String claimId);
    }

    interface StateTransitionRepository {
        boolean updateState(String claimId, String targetState);
    }

    interface EmailNotificationService {
        void sendNotification(String claimId, String subject, String body);
    }

    // Service under test orchestrating duplicate detection and state transition
    static class InsuredEngagementService {
        private final DuplicateDetectionRepository duplicateDetectionRepository;
        private final StateTransitionRepository stateTransitionRepository;
        private final EmailNotificationService emailNotificationService;

        InsuredEngagementService(DuplicateDetectionRepository duplicateDetectionRepository,
                                 StateTransitionRepository stateTransitionRepository,
                                 EmailNotificationService emailNotificationService) {
            this.duplicateDetectionRepository = duplicateDetectionRepository;
            this.stateTransitionRepository = stateTransitionRepository;
            this.emailNotificationService = emailNotificationService;
        }

        boolean processClaim(String claimId) {
            int duplicateCount = duplicateDetectionRepository.findDuplicates(claimId);
            boolean isDuplicate = duplicateCount > 0;

            String nextState = isDuplicate ? "FLAGGED_FOR_REVIEW" : TARGET_STATE;
            boolean transitionSuccess = stateTransitionRepository.updateState(claimId, nextState);

            if (transitionSuccess) {
                emailNotificationService.sendNotification(claimId, "Claim State Updated", "State transition completed.");
            }

            return transitionSuccess;
        }
    }
}
