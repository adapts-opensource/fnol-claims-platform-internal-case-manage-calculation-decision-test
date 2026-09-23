package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionTransformationMockTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private MatchResultService matchResultService;

    @InjectMocks
    private DecisionTransformationService decisionTransformationService;

    @BeforeEach
    void setUp() {
        // Default mock behavior for decision transformation context
        lenient().when(policyValidationService.checkStatus(anyString())).thenReturn(true);
    }

    @Test
    void decision_match_result_rule_if_confidence_0_9_and_policy_active_matched_expected_outcome_proceed_to_duplicate_check_and_claim_creation() {
        // Arrange
        String policyId = "POL-10029";
        double confidenceScore = 0.95;
        boolean expectedActive = true;

        when(policyValidationService.checkStatus(policyId)).thenReturn(expectedActive);

        // Act
        DecisionOutcome outcome = decisionTransformationService.transform(policyId, confidenceScore);

        // Assert
        assertEquals("Matched", outcome.getDecision());
        assertEquals("Proceed to duplicate check and claim creation", outcome.getNextStep());

        // Verify external service interactions
        verify(matchResultService).recordMatch(policyId, "Matched", confidenceScore);
        verify(policyValidationService).checkStatus(policyId);
    }

    // Supporting types for test isolation and compilation completeness
    static class DecisionOutcome {
        private final String decision;
        private final String nextStep;

        DecisionOutcome(String decision, String nextStep) {
            this.decision = decision;
            this.nextStep = nextStep;
        }

        String getDecision() { return decision; }
        String getNextStep() { return nextStep; }
    }

    interface PolicyValidationService {
        boolean checkStatus(String policyId);
    }

    interface MatchResultService {
        void recordMatch(String policyId, String decision, double confidence);
    }

    static class DecisionTransformationService {
        private final PolicyValidationService policyValidationService;
        private final MatchResultService matchResultService;

        DecisionTransformationService(PolicyValidationService policyValidationService, MatchResultService matchResultService) {
            this.policyValidationService = policyValidationService;
            this.matchResultService = matchResultService;
        }

        DecisionOutcome transform(String policyId, double confidence) {
            boolean isActive = policyValidationService.checkStatus(policyId);
            String decision = (confidence >= 0.9 && isActive) ? "Matched" : "Unmatched";
            String nextStep = decision.equals("Matched") ? "Proceed to duplicate check and claim creation" : "Manual Review";
            matchResultService.recordMatch(policyId, decision, confidence);
            return new DecisionOutcome(decision, nextStep);
        }
    }
}
