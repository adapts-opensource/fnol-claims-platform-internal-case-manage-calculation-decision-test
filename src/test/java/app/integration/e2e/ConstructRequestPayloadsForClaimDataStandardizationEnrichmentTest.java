package app.integration.e2e;

import app.models.ClaimDataStandardizationDecisionValidation;
import app.services.ClaimDataStandardizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class ConstructRequestPayloadsForClaimDataStandardizationEnrichmentTest {

    private ClaimDataStandardizationService claimDataStandardizationService;
    private final String expectedRequestId = "REQ-CLM-STD-001";
    private final Map<String, Object> testPayload = Map.of(
            "claimId", "CLM-12345-TEST",
            "policyNumber", "POL-98765-TEST",
            "incidentDate", "2024-01-15",
            "damageType", "COLLISION",
            "estimatedAmount", 1500.00
    );

    @BeforeEach
    void setUp() {
        claimDataStandardizationService = new ClaimDataStandardizationService();
    }

    @Test
    void construct_request_payloads_for_claim_data_standardization_enrichment_validation_without_mocks() {
        ClaimDataStandardizationDecisionValidation result =
                claimDataStandardizationService.constructAndValidatePayload(expectedRequestId, testPayload);

        assertNotNull(result, "Validation result must not be null");
        assertEquals(expectedRequestId, result.getId(), "Request ID must match the constructed payload");
        assertEquals(testPayload, result.getPayload(), "Payload content must match the input fixture");
        assertTrue(result.isValid(), "Enrichment validation must pass for standard claim data");
    }
}
