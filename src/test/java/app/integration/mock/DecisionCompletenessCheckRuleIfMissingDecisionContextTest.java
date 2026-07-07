package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionCompletenessCheckRuleIfMissingDecisionContextTest {

    @Mock
    private DecisionContextGateway decisionContextGateway;

    @Mock
    private RemediationAlertGateway remediationAlertGateway;

    private DecisionTransformationService decisionTransformationService;

    @BeforeEach
    void setUp() {
        decisionTransformationService = new DecisionTransformationService(decisionContextGateway, remediationAlertGateway);
    }

    @Test
    void decision_completeness_check_rule_if_missing_decision_context_alert_expected_outcome_flag_for_remediation() {
        // Arrange: Simulate missing decision context (e.g., not found in DynamoDB)
        String insuredEngagementId = "ENG-98765";
        when(decisionContextGateway.fetchContext(insuredEngagementId)).thenReturn(Optional.empty());

        // Act: Execute the transformation rule
        TransformationOutcome outcome = decisionTransformationService.applyCompletenessCheck(insuredEngagementId);

        // Assert: Verify expected outcome flags for remediation
        assertNotNull(outcome, "Transformation outcome should not be null");
        assertTrue(outcome.isFlaggedForRemediation(), "Outcome must be flagged for remediation when context is missing");
        assertEquals("MISSING_DECISION_CONTEXT", outcome.getAlertCode(), "Alert code should indicate missing context");
        verify(remediationAlertGateway, times(1))
            .sendRemediationAlert(eq(insuredEngagementId), eq("MISSING_DECISION_CONTEXT"), anyString());
    }

    // Minimal supporting types for compilation context
    static class DecisionTransformationService {
        private final DecisionContextGateway decisionContextGateway;
        private final RemediationAlertGateway remediationAlertGateway;

        DecisionTransformationService(DecisionContextGateway decisionContextGateway, RemediationAlertGateway remediationAlertGateway) {
            this.decisionContextGateway = decisionContextGateway;
            this.remediationAlertGateway = remediationAlertGateway;
        }

        TransformationOutcome applyCompletenessCheck(String engagementId) {
            Optional<DecisionContext> context = decisionContextGateway.fetchContext(engagementId);
            if (context.isEmpty()) {
                remediationAlertGateway.sendRemediationAlert(
                    engagementId,
                    "MISSING_DECISION_CONTEXT",
                    "Decision context is missing. Manual review required."
                );
                return new TransformationOutcome(true, "MISSING_DECISION_CONTEXT");
            }
            return new TransformationOutcome(false, null);
        }
    }

    interface DecisionContextGateway {
        Optional<DecisionContext> fetchContext(String engagementId);
    }

    interface RemediationAlertGateway {
        void sendRemediationAlert(String engagementId, String alertCode, String message);
    }

    record DecisionContext(String id, String type) {}

    record TransformationOutcome(boolean flaggedForRemediation, String alertCode) {
        public boolean isFlaggedForRemediation() { return flaggedForRemediation; }
        public String getAlertCode() { return alertCode; }
    }
}
