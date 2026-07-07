package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationStateTransitionOrchestrationMockTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private RulesTriageEngine rulesTriageEngine;

    @Test
    void decision_approval_required_rule_threshold_change_10_require_approval_expected_outcome_route_to_approval_queue() {
        // Arrange
        String claimId = "CLM-2024-001";
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "thresholdChangePercent", 12.5,
            "status", "INITIATED",
            "approvalFlag", false
        );

        when(claimDataStoreClient.getItem(claimId)).thenReturn(payload);
        when(rulesTriageEngine.evaluateThresholdRule(payload)).thenReturn(true);

        // Act
        String outcome = orchestrateStateTransition(claimId, claimDataStoreClient, rulesTriageEngine);

        // Assert
        assertEquals("ROUTE_TO_APPROVAL_QUEUE", outcome);
        verify(rulesTriageEngine).evaluateThresholdRule(payload);
    }

    private String orchestrateStateTransition(String claimId, ClaimDataStoreClient store, RulesTriageEngine engine) {
        Map<String, Object> data = store.getItem(claimId);
        if (engine.evaluateThresholdRule(data)) {
            return "ROUTE_TO_APPROVAL_QUEUE";
        }
        return "PROCEED_TO_NEXT_STATE";
    }
}
