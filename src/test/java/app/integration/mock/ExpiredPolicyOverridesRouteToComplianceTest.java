package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ExpiredPolicyOverridesRouteToComplianceTest {

    @Mock
    private PolicyStatusService policyStatusService;

    @InjectMocks
    private ClaimRoutingDecisionCalculator routingDecisionCalculator;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and cleanup automatically.
        // Additional setup for shared state or test fixtures can go here.
    }

    @Test
    void expired_policy_overrides_route_to_compliance() {
        // Given: Claim initiation payload with an expired policy identifier
        String claimId = "CLM-INIT-9981";
        String expiredPolicyId = "POL-EXP-4420";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("policyId", expiredPolicyId);
        payload.put("claimType", "AUTO_COLLISION");
        payload.put("insuredName", "Test Insured");
        payload.put("submissionTimestamp", "2024-05-20T14:30:00Z");

        // Mock external policy lookup service to simulate an expired policy status
        when(policyStatusService.checkStatus(expiredPolicyId)).thenReturn(PolicyStatus.EXPIRED);

        // When: Calculate routing decision based on the initiation payload
        RoutingDecision decision = routingDecisionCalculator.calculate(payload);

        // Then: Verify the decision overrides standard routing and routes to compliance
        assertEquals(RoutingTarget.COMPLIANCE, decision.getTarget(),
                "Expired policy status should trigger compliance override routing");
    }

    // Supporting domain types for self-contained mock compilation
    enum PolicyStatus { ACTIVE, EXPIRED, CANCELLED }
    enum RoutingTarget { STANDARD, COMPLIANCE, UNDERWRITING }

    static class RoutingDecision {
        private final RoutingTarget target;
        public RoutingDecision(RoutingTarget target) { this.target = target; }
        public RoutingTarget getTarget() { return target; }
    }

    interface PolicyStatusService {
        PolicyStatus checkStatus(String policyId);
    }

    class ClaimRoutingDecisionCalculator {
        private final PolicyStatusService policyStatusService;

        public ClaimRoutingDecisionCalculator(PolicyStatusService policyStatusService) {
            this.policyStatusService = policyStatusService;
        }

        public RoutingDecision calculate(Map<String, Object> payload) {
            String policyId = (String) payload.get("policyId");
            if (policyId == null) {
                return new RoutingDecision(RoutingTarget.STANDARD);
            }
            PolicyStatus status = policyStatusService.checkStatus(policyId);
            // Business rule: Expired policies override standard routing logic to compliance
            if (status == PolicyStatus.EXPIRED) {
                return new RoutingDecision(RoutingTarget.COMPLIANCE);
            }
            return new RoutingDecision(RoutingTarget.STANDARD);
        }
    }
}
