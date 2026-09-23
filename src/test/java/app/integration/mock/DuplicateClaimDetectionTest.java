package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DuplicateClaimDetectionTest {

    @Mock
    private SimilarityMatcher similarityMatcher;

    @Mock
    private TaskService taskService;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private FnolStateService fnolStateService;

    @InjectMocks
    private ClaimDecisionValidator claimDecisionValidator;

    private Map<String, Object> duplicateClaimInputs;

    @BeforeEach
    void setUp() {
        duplicateClaimInputs = Map.of(
                "policy_number", "POL-8842",
                "risk_address", "456 Oak Ave",
                "loss_date", "2024-08-10",
                "cause_of_loss", "wind",
                "prior_claim_id", "CLM-FL01-2024-00009921"
        );
    }

    @Test
    void validate_duplicate_claim_detection_and_routing() {
        // Arrange: Mock high similarity score to trigger duplicate detection
        double expectedMatchScore = 0.95;
        when(similarityMatcher.calculate(anyMap(), anyString())).thenReturn(expectedMatchScore);

        // Act: Execute the decision validation logic
        claimDecisionValidator.processDecisionValidation(duplicateClaimInputs);

        // Assert: Verify system identifies high similarity
        verify(similarityMatcher).calculate(anyMap(), eq("POL-8842"));

        // Assert: FNOL state set to Duplicate Review
        verify(fnolStateService).setState(eq("Duplicate Review"));

        // Assert: Task created as Review Potential Duplicate Claim
        verify(taskService).createTask(eq("Review Potential Duplicate Claim"), eq("CLM-FL01-2024-00009921"));

        // Assert: Audit log captures duplicate_match_score
        verify(auditLogger).logMetric(eq("duplicate_match_score"), eq(expectedMatchScore));

        // Verify no other state/task mutations occurred
        verifyNoMoreInteractions(fnolStateService, taskService, auditLogger);
    }

    // Minimal dependency interfaces for isolated mocking
    private interface SimilarityMatcher {
        double calculate(Map<String, Object> claimData, String policyNumber);
    }

    private interface TaskService {
        void createTask(String taskType, String claimId);
    }

    private interface AuditLogger {
        void logMetric(String metricName, double value);
    }

    private interface FnolStateService {
        void setState(String newState);
    }

    // Service under test
    private static class ClaimDecisionValidator {
        private final SimilarityMatcher similarityMatcher;
        private final TaskService taskService;
        private final AuditLogger auditLogger;
        private final FnolStateService fnolStateService;

        ClaimDecisionValidator(SimilarityMatcher similarityMatcher, TaskService taskService,
                               AuditLogger auditLogger, FnolStateService fnolStateService) {
            this.similarityMatcher = similarityMatcher;
            this.taskService = taskService;
            this.auditLogger = auditLogger;
            this.fnolStateService = fnolStateService;
        }

        void processDecisionValidation(Map<String, Object> inputs) {
            String policyNumber = (String) inputs.get("policy_number");
            double score = similarityMatcher.calculate(inputs, policyNumber);

            if (score > 0.90) {
                fnolStateService.setState("Duplicate Review");
                taskService.createTask("Review Potential Duplicate Claim", (String) inputs.get("prior_claim_id"));
                auditLogger.logMetric("duplicate_match_score", score);
            }
        }
    }
}
