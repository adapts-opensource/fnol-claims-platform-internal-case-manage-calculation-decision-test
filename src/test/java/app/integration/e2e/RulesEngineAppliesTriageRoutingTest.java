package app.integration.e2e;
import app.models.ClaimInitiationRoutingDecisionValidation;
import app.services.RoutingDecisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class UsFnol04RulesEngineAppliesTriageRoutingTest {

    private RoutingDecisionService routingDecisionService;
    private ClaimInitiationRoutingDecisionValidation claimValidation;
    private Map<String, Object> constants;

    @BeforeEach
    void setUp() {
        routingDecisionService = new RoutingDecisionService();
        claimValidation = new ClaimInitiationRoutingDecisionValidation();
        constants = loadConstants();
    }

    private Map<String, Object> loadConstants() {
        return java.util.Map.of(
            "claim_context", java.util.Map.of(
                "policy_matched", true,
                "date_of_loss_validated", true,
                "product_form_confirmed", true,
                "claim_id", "CLM-TEST-001",
                "routing_decision", "STANDARD_TRIAGE"
            ),
            "expected_routing_result", java.util.Map.of(
                "status", "ROUTED",
                "triage_path", "STANDARD",
                "validation_passed", true
            )
        );
    }

    @Test
    void us_fnol_04_rules_engine_applies_triage_routing() {
        Map<String, Object> contextData = (Map<String, Object>) constants.get("claim_context");
        Map<String, Object> expected = (Map<String, Object>) constants.get("expected_routing_result");

        claimValidation.setId((String) contextData.get("claim_id"));
        claimValidation.setPayload(contextData);

        routingDecisionService.processClaimInitiation(claimValidation);
        routingDecisionService.evaluateRoutingRules(claimValidation);

        Map<String, Object> actualPayload = claimValidation.getPayload();
        assertEquals(expected.get("status"), actualPayload.get("routing_status"));
        assertEquals(expected.get("triage_path"), actualPayload.get("triage_routing_path"));
        assertEquals(expected.get("validation_passed"), actualPayload.get("validation_passed"));
    }
}
