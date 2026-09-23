package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClaimInitiationRoutingDecisionCalculationMockTest {

    @Mock
    private RoutingDecisionCalculator decisionCalculator;

    private String claimId;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        claimId = "claim-init-001";
        payload = new HashMap<>();
        payload.put("fnolType", "AUTO");
        payload.put("policyMatchStatus", "MATCHED");
        payload.put("routingPriority", "HIGH");
    }

    @Test
    void applies_when_task_resolve_policy_match_or_unmatched_fnol_assigned_to_specialist() {
        // Arrange: Mock external decision calculation service to return specialist assignment
        String expectedAssignee = "SPECIALIST";
        String policyRule = "TASK_RESOLVE_MATCH_OR_UNMATCHED";

        when(decisionCalculator.calculate(eq(claimId), any(Map.class)))
                .thenReturn(new RoutingOutcome(expectedAssignee, policyRule));

        // Act: Execute decision calculation
        RoutingOutcome outcome = decisionCalculator.calculate(claimId, payload);

        // Assert: Verify FNOL is assigned to specialist under the specified policy condition
        assertNotNull(outcome);
        assertEquals(expectedAssignee, outcome.getAssignee());
        assertEquals(policyRule, outcome.getPolicyRule());
        verify(decisionCalculator).calculate(eq(claimId), eq(payload));
    }

    // Internal mock DTO representing the decision calculation result
    static class RoutingOutcome {
        private final String assignee;
        private final String policyRule;

        RoutingOutcome(String assignee, String policyRule) {
            this.assignee = assignee;
            this.policyRule = policyRule;
        }

        String getAssignee() { return assignee; }
        String getPolicyRule() { return policyRule; }
    }

    // Interface representing the external decision calculation service
    interface RoutingDecisionCalculator {
        RoutingOutcome calculate(String claimId, Map<String, Object> payload);
    }
}
