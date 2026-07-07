package app.integration.e2e;

import app.models.ClaimDataStandardizationCalculationTransform;
import app.services.ClaimCalculationTransformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClaimDataStandardizationCalculationTransformationE2eTest {

    private ClaimCalculationTransformationService transformationService;

    @BeforeEach
    void setUp() {
        // E2E: Instantiate real application service (compile stubs generated under src/main/java/app/ if needed)
        transformationService = new ClaimCalculationTransformationService();
    }

    @Test
    void construct_request_payloads_for_claim_data_standardization_calculation_transformation_without_mocks() {
        // 1. Build inputs from test-case Inputs / Expected Results and Constants JSON sidecar
        String expectedId = "claim_std_001";
        Map<String, Object> rawInputs = new HashMap<>();
        rawInputs.put("premiumAmount", 1500.00);
        rawInputs.put("deductible", 250.00);
        rawInputs.put("policyType", "AUTO");
        rawInputs.put("claimDate", "2023-10-01T12:00:00Z");

        // 2. Invoke real service to construct and standardize the request payload
        ClaimDataStandardizationCalculationTransform transformedEntity =
                transformationService.constructRequestPayload(expectedId, rawInputs);

        // 3. Assert expected results based on standardization transformation rules
        assertNotNull(transformedEntity, "Transformed entity must not be null");
        assertEquals(expectedId, transformedEntity.getId(), "ID must match input claim identifier");
        assertNotNull(transformedEntity.getPayload(), "Payload map must be constructed");

        Map<String, Object> payload = transformedEntity.getPayload();
        assertEquals(1500.00, payload.get("premiumAmount"), "Premium amount must be preserved");
        assertEquals(250.00, payload.get("deductible"), "Deductible must be preserved");
        assertEquals("AUTO", payload.get("policyType"), "Policy type must be standardized");
        assertTrue(payload.containsKey("transformationTimestamp"), "Payload must include transformation metadata");
        assertEquals("V1", payload.get("standardVersion"), "Standard version must be set");
        assertEquals("SUCCESS", payload.get("transformationStatus"), "Transformation status must indicate success");
    }
}
