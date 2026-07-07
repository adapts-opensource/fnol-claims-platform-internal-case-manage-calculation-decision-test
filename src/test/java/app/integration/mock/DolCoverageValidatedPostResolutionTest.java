package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DolCoverageValidatedPostResolutionTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimPayload = Map.of(
            "id", "claim-101",
            "dateOfLoss", "2023-12-01",
            "coverageType", "COLLISION",
            "resolutionStatus", "RESOLVED"
        );
    }

    @Test
    void dolCoverageValidatedPostResolution() {
        // Arrange: Mock S3 document retrieval for claim data
        when(documentStoreService.getObjectUri(eq("DocumentStoreService-bucket"), eq("claim-101.json")))
                .thenReturn("s3://DocumentStoreService-bucket/claim-101.json");

        // Arrange: Mock DynamoDB policy validation lookup
        when(policyValidationService.getItemPayload(eq("PolicyValidationService_table"), eq("pk_claim-101")))
                .thenReturn(Map.of("coverageActive", true, "dolValid", true));

        // Act: Execute validation decision logic
        Map<String, Object> decision = ClaimValidationEngine.evaluatePostResolution(claimPayload, documentStoreService, policyValidationService);

        // Assert
        assertNotNull(decision, "Decision payload must not be null");
        assertEquals(true, decision.get("dolValidated"), "Date of Loss should be validated post-resolution");
        assertEquals(true, decision.get("coverageValidated"), "Coverage should be validated post-resolution");
        assertEquals("APPROVED", decision.get("decisionStatus"), "Decision status should reflect successful validation");
    }

    // Minimal in-memory engine to simulate feature logic
    static class ClaimValidationEngine {
        public static Map<String, Object> evaluatePostResolution(Map<String, Object> payload,
                                                                 DocumentStoreService docSvc,
                                                                 PolicyValidationService polSvc) {
            String docUri = docSvc.getObjectUri("DocumentStoreService-bucket", payload.get("id") + ".json");
            assertNotNull(docUri, "Document URI must be resolved");

            Map<String, Object> policyData = polSvc.getItemPayload("PolicyValidationService_table", "pk_" + payload.get("id"));
            boolean dolValid = Boolean.TRUE.equals(policyData.get("dolValid"));
            boolean coverageValid = Boolean.TRUE.equals(policyData.get("coverageActive"));

            return Map.of(
                "id", payload.get("id"),
                "dolValidated", dolValid,
                "coverageValidated", coverageValid,
                "decisionStatus", (dolValid && coverageValid) ? "APPROVED" : "REJECTED"
            );
        }
    }

    // Mock service contracts to satisfy compilation without live AWS calls
    interface DocumentStoreService {
        String getObjectUri(String bucketName, String objectKey);
    }

    interface PolicyValidationService {
        Map<String, Object> getItemPayload(String tableName, String partitionKey);
    }
}
