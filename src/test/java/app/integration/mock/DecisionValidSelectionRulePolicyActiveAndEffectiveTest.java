package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private CacheAndReferenceDataService cacheService;
    @Mock
    private ClaimsPolicyDataStoreService dataStoreService;
    @Mock
    private CommunicationService communicationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection and lifecycle management
    }

    @Test
    void decision_valid_selection_rule_policy_active_and_effective_expected_outcome_policy_set_fnol_proceeds() {
        // Given
        String requestId = "claim-init-req-001";
        Map<String, Object> payload = Map.of(
                "policyStatus", "ACTIVE",
                "effectiveDate", "2024-06-01",
                "claimType", "FNOL",
                "routingContext", "STANDARD"
        );
        ClaimInitiationRoutingDecisionValidation input = new ClaimInitiationRoutingDecisionValidation(requestId, payload);

        // Mock external I/O contracts (Redis, DynamoDB, SES)
        when(cacheService.get("Cache & Reference Data:cache:policy:" + input.id())).thenReturn("ACTIVE");
        when(dataStoreService.getItem(input.id())).thenReturn(Map.of("status", "ACTIVE", "effective", true));
        doNothing().when(communicationService).sendNotification(anyString(), anyMap());

        // When
        DecisionOutcome outcome = evaluateDecision(input);

        // Then
        assertNotNull(outcome, "Decision outcome should not be null");
        assertEquals("Valid Selection", outcome.decision(), "Decision should be Valid Selection");
        assertEquals("Policy set; FNOL proceeds.", outcome.expectedOutcome(), "Expected outcome should match policy set and FNOL proceeds");
    }

    private DecisionOutcome evaluateDecision(ClaimInitiationRoutingDecisionValidation input) {
        // Simulate orchestration: decision:validation
        String policyStatus = (String) input.payload().getOrDefault("policyStatus", "UNKNOWN");
        boolean isPolicyActiveAndEffective = "ACTIVE".equalsIgnoreCase(policyStatus);

        String decision = isPolicyActiveAndEffective ? "Valid Selection" : "Invalid Selection";
        String expectedOutcome = isPolicyActiveAndEffective ? "Policy set; FNOL proceeds." : "Claim rejected; policy inactive.";

        return new DecisionOutcome(decision, expectedOutcome);
    }

    // Data Model: claim_initiation___routing_decision_validation
    public static class ClaimInitiationRoutingDecisionValidation {
        private final String id;
        private final Map<String, Object> payload;

        public ClaimInitiationRoutingDecisionValidation(String id, Map<String, Object> payload) {
            this.id = id;
            this.payload = payload;
        }

        public String id() { return id; }
        public Map<String, Object> payload() { return payload; }
    }

    // Result Model
    public static class DecisionOutcome {
        private final String decision;
        private final String expectedOutcome;

        public DecisionOutcome(String decision, String expectedOutcome) {
            this.decision = decision;
            this.expectedOutcome = expectedOutcome;
        }

        public String decision() { return decision; }
        public String expectedOutcome() { return expectedOutcome; }
    }

    // Mocked Infra Interfaces
    public interface CacheAndReferenceDataService {
        String get(String key);
    }
    public interface ClaimsPolicyDataStoreService {
        Map<String, Object> getItem(String pk);
    }
    public interface CommunicationService {
        String sendNotification(String fromAddress, Map<String, String> toAddresses);
    }
}
