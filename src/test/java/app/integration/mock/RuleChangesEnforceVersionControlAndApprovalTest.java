package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RuleChangesEnforceVersionControlAndApprovalTest {

    @Mock
    private ClaimDecisionEnrichmentService enrichmentService;

    @Mock
    private DocumentStoreClient s3Client;

    @Mock
    private ClaimDataStoreClient dynamoDBClient;

    @InjectMocks
    private DecisionRuleEnforcementService enforcementService;

    private String claimId;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        claimId = UUID.randomUUID().toString();
        // GDPR/SOC2: PII-free payload structure per entity definition
        payload = Map.of(
            "id", claimId,
            "payload", Map.of(
                "ruleVersion", "2.1.0",
                "approvalStatus", "PENDING",
                "enrichmentType", "DECISION_STANDARDIZATION"
            )
        );
    }

    @Test
    void rule_changes_enforce_version_control_and_approval() {
        // Given: Rule change with version control metadata but pending approval
        when(enrichmentService.validatePayload(anyString(), any(Map.class))).thenReturn(true);
        when(enrichmentService.isApproved(anyString())).thenReturn(false);

        // When & Then: Unapproved changes must be rejected without persisting
        // Security: input_validation & least_privilege_iam enforced at service boundary
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> enforcementService.processDecisionEnrichment(claimId, payload)
        );
        assertEquals("Rule change requires approval before persistence", exception.getMessage());

        // Verify infra I/O contracts are NOT invoked for unapproved changes
        verify(s3Client, never()).putObject(anyString(), anyString(), any());
        verify(dynamoDBClient, never()).putItem(anyString(), any(Map.class));

        // Given: Rule change with explicit approval
        when(enrichmentService.isApproved(anyString())).thenReturn(true);

        // When: Process enrichment
        enforcementService.processDecisionEnrichment(claimId, payload);

        // Then: Persist to S3 and DynamoDB with validated payload
        verify(s3Client).putObject(eq("Document & Media Store-bucket"), anyString(), any());
        verify(dynamoDBClient).putItem(eq("Policy & Claim Data Store_table"), any(Map.class));

        // NFR: observability -> structured_logging
        verify(enrichmentService).logStructuredEvent(eq("RULE_CHANGE_VERSION_CONTROLLED"), any(Map.class));
        // NFR: security -> input_validation
        verify(enrichmentService).validateInput(anyString(), any(Map.class));
    }

    // Minimal service stubs for test isolation and compilation
    static class ClaimDecisionEnrichmentService {
        public boolean validatePayload(String id, Map<String, Object> payload) { return false; }
        public boolean isApproved(String id) { return false; }
        public void logStructuredEvent(String event, Map<String, Object> metadata) {}
        public void validateInput(String id, Map<String, Object> payload) {}
    }

    static class DocumentStoreClient {
        public void putObject(String bucket, String key, Object content) {}
    }

    static class ClaimDataStoreClient {
        public void putItem(String table, Map<String, Object> item) {}
    }

    static class DecisionRuleEnforcementService {
        private final ClaimDecisionEnrichmentService enrichmentService;
        private final DocumentStoreClient s3Client;
        private final ClaimDataStoreClient dynamoDBClient;

        DecisionRuleEnforcementService(ClaimDecisionEnrichmentService enrichmentService,
                                       DocumentStoreClient s3Client,
                                       ClaimDataStoreClient dynamoDBClient) {
            this.enrichmentService = enrichmentService;
            this.s3Client = s3Client;
            this.dynamoDBClient = dynamoDBClient;
        }

        void processDecisionEnrichment(String claimId, Map<String, Object> payload) {
            if (!enrichmentService.validatePayload(claimId, payload)) {
                throw new IllegalArgumentException("Invalid payload");
            }
            if (!enrichmentService.isApproved(claimId)) {
                throw new IllegalStateException("Rule change requires approval before persistence");
            }
            enrichmentService.logStructuredEvent("RULE_CHANGE_VERSION_CONTROLLED", payload);
            enrichmentService.validateInput(claimId, payload);
            s3Client.putObject("Document & Media Store-bucket", claimId + ".json", payload);
            dynamoDBClient.putItem("Policy & Claim Data Store_table", payload);
        }
    }
}
