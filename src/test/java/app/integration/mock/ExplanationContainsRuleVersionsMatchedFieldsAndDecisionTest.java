package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationEnrichmentValidationTest {

    interface DocumentMediaStoreClient {
        Map<String, Object> getObject(String bucketName, String objectKeyPattern);
    }

    interface PolicyClaimDataStoreClient {
        Map<String, Object> putItem(String tableName, Map<String, Object> item);
    }

    class ClaimDataStandardizationEnrichmentValidator {
        private final DocumentMediaStoreClient s3Client;
        private final PolicyClaimDataStoreClient dynamoDbClient;

        ClaimDataStandardizationEnrichmentValidator(DocumentMediaStoreClient s3Client, PolicyClaimDataStoreClient dynamoDbClient) {
            this.s3Client = s3Client;
            this.dynamoDbClient = dynamoDbClient;
        }

        String enrichAndValidate(String id, Map<String, Object> payload) {
            Map<String, Object> docData = s3Client.getObject("Document & Media Store-bucket", "Document & Media Store/" + id + ".json");
            dynamoDbClient.putItem("Policy & Claim Data Store_table", Map.of("id", id, "payload", docData));
            return "RuleVersions: v1.2, v2.0; MatchedFields: [claimType, coverage]; DecisionLogic: APPROVED based on riskScore < 50";
        }
    }

    @Mock
    private DocumentMediaStoreClient s3Client;

    @Mock
    private PolicyClaimDataStoreClient dynamoDbClient;

    private ClaimDataStandardizationEnrichmentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ClaimDataStandardizationEnrichmentValidator(s3Client, dynamoDbClient);
    }

    @Test
    void explanation_contains_rule_versions_matched_fields_and_decision_logic() {
        String entityId = "claim_789";
        Map<String, Object> inputPayload = Map.of("claimType", "AUTO", "coverageAmount", 50000, "riskScore", 30);

        when(s3Client.getObject("Document & Media Store-bucket", "Document & Media Store/" + entityId + ".json"))
                .thenReturn(inputPayload);
        when(dynamoDbClient.putItem(eq("Policy & Claim Data Store_table"), any(Map.class)))
                .thenReturn(Map.of("ItemPayload", Map.of("id", entityId, "status", "VALIDATED")));

        String explanation = validator.enrichAndValidate(entityId, inputPayload);

        assertNotNull(explanation);
        assertTrue(explanation.contains("RuleVersions"), "Explanation must contain rule versions");
        assertTrue(explanation.contains("MatchedFields"), "Explanation must contain matched fields");
        assertTrue(explanation.contains("DecisionLogic"), "Explanation must contain decision logic");

        verify(s3Client).getObject("Document & Media Store-bucket", "Document & Media Store/" + entityId + ".json");
        verify(dynamoDbClient).putItem(eq("Policy & Claim Data Store_table"), any(Map.class));
    }
}
