package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * Mock integration test for Insured Engagement & Tracking state transition decision logic.
 * Verifies success and failure output criteria per feature specification.
 */
public class StateTransitionInsuredEngagementMockTest {

    @Mock
    private RuleEngineService ruleEngineService;

    @Mock
    private ClaimStateService claimStateService;

    @InjectMocks
    private StateTransitionHandler stateTransitionHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void output_criteria_success_outputs_duplicate_flag_boolean_similarity_score_recommended_action_matched_claim_id_failure_outputs_query_timeout_rule_engine_error_fallback_to_manual_review() {
        // --- Success Scenario ---
        Map<String, Object> expectedSuccess = new HashMap<>();
        expectedSuccess.put("duplicate_flag", true);
        expectedSuccess.put("similarity_score", 0.92);
        expectedSuccess.put("recommended_action", "auto_approve");
        expectedSuccess.put("matched_claim_id", "CLM-98765");

        when(ruleEngineService.evaluate(anyString())).thenReturn(expectedSuccess);
        when(claimStateService.updateState(anyString(), anyString())).thenReturn(true);

        Map<String, Object> successResult = stateTransitionHandler.processDecision("CLM-123", "UNDER_REVIEW");

        assertNotNull(successResult);
        assertTrue((Boolean) successResult.get("duplicate_flag"));
        assertEquals(0.92, (Double) successResult.get("similarity_score"), 0.01);
        assertEquals("auto_approve", successResult.get("recommended_action"));
        assertEquals("CLM-98765", successResult.get("matched_claim_id"));
        assertNull(successResult.get("query_timeout"));
        assertNull(successResult.get("rule_engine_error"));
        assertNull(successResult.get("fallback_to_manual_review"));

        // --- Failure Scenario (Query Timeout / Rule Engine Error) ---
        when(ruleEngineService.evaluate(anyString())).thenThrow(new RuntimeException("Query timeout or rule engine error"));
        when(claimStateService.updateState(anyString(), anyString())).thenReturn(true);

        Map<String, Object> failureResult = stateTransitionHandler.processDecision("CLM-123", "UNDER_REVIEW");

        assertNotNull(failureResult);
        assertTrue((Boolean) failureResult.get("query_timeout"));
        assertTrue((Boolean) failureResult.get("rule_engine_error"));
        assertTrue((Boolean) failureResult.get("fallback_to_manual_review"));
        assertEquals("manual_review", failureResult.get("recommended_action"));
        assertNull(failureResult.get("matched_claim_id"));
        assertNull(failureResult.get("similarity_score"));
        assertNull(failureResult.get("duplicate_flag"));
    }

    // Minimal service interfaces to ensure mock compilation without external dependencies
    interface RuleEngineService {
        Map<String, Object> evaluate(String claimId);
    }

    interface ClaimStateService {
        boolean updateState(String claimId, String newState);
    }

    /**
     * Simplified handler simulating the state_transition decision flow.
     */
    static class StateTransitionHandler {
        private final RuleEngineService ruleEngineService;
        private final ClaimStateService claimStateService;

        StateTransitionHandler(RuleEngineService ruleEngineService, ClaimStateService claimStateService) {
            this.ruleEngineService = ruleEngineService;
            this.claimStateService = claimStateService;
        }

        Map<String, Object> processDecision(String claimId, String newState) {
            Map<String, Object> outputs = new HashMap<>();
            try {
                Map<String, Object> engineResult = ruleEngineService.evaluate(claimId);
                claimStateService.updateState(claimId, newState);
                outputs.putAll(engineResult);
            } catch (Exception e) {
                outputs.put("query_timeout", true);
                outputs.put("rule_engine_error", true);
                outputs.put("fallback_to_manual_review", true);
                outputs.put("recommended_action", "manual_review");
            }
            return outputs;
        }
    }
}
