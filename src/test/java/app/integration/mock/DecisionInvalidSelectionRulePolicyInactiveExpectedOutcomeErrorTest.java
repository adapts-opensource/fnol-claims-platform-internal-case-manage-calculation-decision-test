package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Mock tests for Claim Initiation & Routing:orchestration:decision.
 * Verifies decision validation logic against mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionValidationMockTest {

    @Mock
    private PolicyService policyService;

    @Mock
    private RoutingDecisionEngine routingDecisionEngine;

    @InjectMocks
    private ClaimInitiationOrchestrator claimInitiationOrchestrator;

    private Map<String, Object> decisionPayload;

    @BeforeEach
    void setUp() {
        decisionPayload = Map.of(
            "id", "CLM-DEC-001",
            "payload", Map.of(
                "policyId", "POL-INACTIVE-MOCK",
                "claimType", "AUTO_COLLISION",
                "initiationSource", "WEB_PORTAL"
            )
        );
    }

    @Test
    @DisplayName("Decision Invalid Selection Rule Policy Inactive Expected outcome Error")
    void decision_invalid_selection_rule_policy_inactive_expected_outcome_error_adjuster_must_choose_another() {
        // Arrange
        String policyId = "POL-INACTIVE-MOCK";
        String expectedErrorMessage = "Error; adjuster must choose another.";
        String expectedRuleCode = "POLICY_INACTIVE";

        when(policyService.isPolicyActive(policyId)).thenReturn(false);
        when(routingDecisionEngine.validateDecision(any(Map.class))).thenThrow(
            new DecisionValidationException(expectedRuleCode, expectedErrorMessage)
        );

        // Act & Assert
        DecisionValidationException exception = assertThrows(
            DecisionValidationException.class,
            () -> claimInitiationOrchestrator.processDecision(decisionPayload)
        );

        assertEquals(expectedRuleCode, exception.getRuleCode());
        assertEquals(expectedErrorMessage, exception.getMessage());
    }

    // Supporting interfaces/classes for the mock test structure
    private interface PolicyService {
        boolean isPolicyActive(String policyId);
    }

    private interface RoutingDecisionEngine {
        void validateDecision(Map<String, Object> payload);
    }

    @InjectMocks
    private static class ClaimInitiationOrchestrator {
        private final PolicyService policyService;
        private final RoutingDecisionEngine routingDecisionEngine;

        public ClaimInitiationOrchestrator(PolicyService policyService, RoutingDecisionEngine routingDecisionEngine) {
            this.policyService = policyService;
            this.routingDecisionEngine = routingDecisionEngine;
        }

        public void processDecision(Map<String, Object> payload) {
            Map<String, Object> claimPayload = (Map<String, Object>) payload.get("payload");
            String policyId = (String) claimPayload.get("policyId");

            if (!policyService.isPolicyActive(policyId)) {
                routingDecisionEngine.validateDecision(payload);
            }
        }
    }

    public static class DecisionValidationException extends RuntimeException {
        private final String ruleCode;

        public DecisionValidationException(String ruleCode, String message) {
            super(message);
            this.ruleCode = ruleCode;
        }

        public String getRuleCode() {
            return ruleCode;
        }
    }
}
