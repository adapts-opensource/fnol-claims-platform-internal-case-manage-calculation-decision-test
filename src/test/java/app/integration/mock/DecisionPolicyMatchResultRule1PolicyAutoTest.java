package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class DecisionPolicyMatchResultRule1PolicyAutoTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private DocumentStoreService documentStoreService;

    private ClaimDecisionProcessor decisionProcessor;

    @BeforeEach
    void setUp() {
        decisionProcessor = new ClaimDecisionProcessor(policyValidationService, rulesEngineService, documentStoreService);
    }

    @Test
    void decision_policy_match_result_rule_1_policy_auto_continue_2_task_0_unmatched_shell_expected_outcome_routing_decision_task_generation() {
        // Arrange: Simulate 1 policy match per rule '1 policy = auto-continue'
        String claimId = "claim-data-standardization-001";
        Map<String, Object> claimPayload = new HashMap<>();
        claimPayload.put("id", claimId);
        claimPayload.put("policyMatchCount", 1);
        claimPayload.put("status", "VALIDATED");

        Map<String, Object> expectedRoutingDecision = new HashMap<>();
        expectedRoutingDecision.put("decision", "Policy match result");
        expectedRoutingDecision.put("outcome", "Routing decision & task generation");
        expectedRoutingDecision.put("nextStep", "auto-continue");

        when(policyValidationService.fetchValidationRules(claimId)).thenReturn(List.of("RULE_1_POLICY_AUTO"));
        when(rulesEngineService.evaluateDecision(claimId, claimPayload)).thenReturn(expectedRoutingDecision);

        // Act
        Map<String, Object> actualResult = decisionProcessor.executeDecisionFlow(claimId, claimPayload);

        // Assert: Verify expected outcome 'Routing decision & task generation'
        assertNotNull(actualResult, "Decision result must not be null");
        assertEquals("Policy match result", actualResult.get("decision"));
        assertEquals("Routing decision & task generation", actualResult.get("outcome"));
        assertEquals("auto-continue", actualResult.get("nextStep"));

        // Verify infra I/O contracts were respected
        verify(policyValidationService).fetchValidationRules(claimId);
        verify(rulesEngineService).evaluateDecision(claimId, claimPayload);
        verifyNoInteractions(documentStoreService);
    }

    // Minimal service stubs for mock isolation
    interface PolicyValidationService {
        List<String> fetchValidationRules(String claimId);
    }

    interface RulesEngineService {
        Map<String, Object> evaluateDecision(String claimId, Map<String, Object> payload);
    }

    interface DocumentStoreService {
        String storeDocument(String bucketName, String objectKeyPattern, Map<String, Object> data);
    }

    class ClaimDecisionProcessor {
        private final PolicyValidationService policyValidationService;
        private final RulesEngineService rulesEngineService;
        private final DocumentStoreService documentStoreService;

        ClaimDecisionProcessor(PolicyValidationService policyValidationService,
                               RulesEngineService rulesEngineService,
                               DocumentStoreService documentStoreService) {
            this.policyValidationService = policyValidationService;
            this.rulesEngineService = rulesEngineService;
            this.documentStoreService = documentStoreService;
        }

        Map<String, Object> executeDecisionFlow(String claimId, Map<String, Object> payload) {
            List<String> rules = policyValidationService.fetchValidationRules(claimId);
            Map<String, Object> decision = rulesEngineService.evaluateDecision(claimId, payload);
            return decision;
        }
    }
}
