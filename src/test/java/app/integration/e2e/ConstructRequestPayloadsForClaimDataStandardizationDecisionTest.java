package app.integration.e2e;

import app.models.ClaimDataStandardizationCalculationTransform;
import app.services.ClaimDataStandardizationDecisionTransformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

public class ClaimDataStandardizationDecisionTransformationE2eTest {

    private ClaimDataStandardizationDecisionTransformationService decisionTransformationService;
    private ClaimDataStandardizationCalculationTransform transformRecord;

    @BeforeEach
    void setUp() {
        // Initialize real application services and models (stubs generated under src/main/java/app/)
        decisionTransformationService = new ClaimDataStandardizationDecisionTransformationService();
        transformRecord = new ClaimDataStandardizationCalculationTransform();
    }

    @Test
    void construct_request_payloads_for_claim_data_standardization_decision_transformation_without_mocks() {
        // Arrange: Build inputs from test-case Inputs / Expected Results and the Constants JSON sidecar
        String testCaseId = "claim-std-7f3a9b";
        Map<String, Object> rawPayload = new HashMap<>();
        rawPayload.put("incidentDate", "2024-05-20");
        rawPayload.put("policyNumber", "POL-998877");
        rawPayload.put("coverageType", "COMPREHENSIVE");
        rawPayload.put("deductibleAmount", 500.00);

        transformRecord.setId(testCaseId);
        transformRecord.setPayload(rawPayload);

        // Act: Invoke real service to construct request payloads for decision transformation
        Map<String, Object> requestPayloads = decisionTransformationService.constructRequestPayloads(transformRecord);

        // Assert: Verify E2E outcome matches expected standardization contract
        assertNotNull(requestPayloads, "Constructed request payloads must not be null");
        assertTrue(requestPayloads.containsKey("decisionTransformOutput"), "Payload must contain decision transform output key");

        @SuppressWarnings("unchecked")
        Map<String, Object> outputData = (Map<String, Object>) requestPayloads.get("decisionTransformOutput");
        
        assertEquals(testCaseId, outputData.get("sourceId"), "Source ID must propagate correctly through transformation");
        assertEquals("COMPREHENSIVE", outputData.get("coverageType"), "Coverage type must be standardized per insurance domain rules");
        assertEquals(500.00, outputData.get("deductibleAmount"), "Deductible amount must be preserved as decimal without precision loss");
        assertTrue(outputData.containsKey("standardizationTimestamp"), "Standardization timestamp must be injected for auditability");
        assertTrue(outputData.containsKey("ruleEngineVersion"), "Rule engine version must be attached for compliance tracking");
    }
}
