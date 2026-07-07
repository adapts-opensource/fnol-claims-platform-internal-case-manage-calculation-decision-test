package app.integration.e2e;

import app.models.ClaimInitiationRoutingDecisionValidation;
import app.services.ClaimInitiationRoutingDecisionCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

class ConstructRequestPayloadsForClaimInitiationRoutingDecisionCalculationTest {

    private ClaimInitiationRoutingDecisionCalculationService calculationService;

    @BeforeEach
    void setUp() {
        calculationService = new ClaimInitiationRoutingDecisionCalculationService();
    }

    @Test
    void construct_request_payloads_for_claim_initiation_routing_decision_calculation_without_mocks() {
        // Build inputs from test-case Inputs / Expected Results and Constants JSON sidecar
        String claimId = "claim-init-001";
        Map<String, Object> requestPayload = Map.of(
            "policyNumber", "POL-2024-001",
            "incidentDate", "2024-05-20",
            "damageEstimate", 5000.00,
            "coverageType", "COMPREHENSIVE",
            "routingRule", "AUTO_CALCULATE_PRIORITY"
        );

        ClaimInitiationRoutingDecisionValidation validationResult =
            calculationService.constructAndValidatePayload(claimId, requestPayload);

        // Assert on handler outcomes
        assertNotNull(validationResult);
        assertEquals(claimId, validationResult.getId());
        assertNotNull(validationResult.getPayload());
        assertEquals("POL-2024-001", validationResult.getPayload().get("policyNumber"));
        assertEquals(5000.00, validationResult.getPayload().get("damageEstimate"));
        assertTrue(validationResult.getPayload().containsKey("routingRule"));
    }
}
