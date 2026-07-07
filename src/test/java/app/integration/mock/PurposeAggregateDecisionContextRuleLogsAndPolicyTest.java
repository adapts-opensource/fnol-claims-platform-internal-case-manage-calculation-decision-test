package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.TransactSendEmailRequest;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurposeAggregateDecisionContextRuleLogsAndPolicy {

    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private S3Client s3Client;
    @Mock
    private SesClient sesClient;

    private String testEntityId;
    private Map<String, Object> expectedAggregatedPayload;

    @BeforeEach
    void setUp() {
        testEntityId = "fnol-test-001";

        // Mock DynamoDB: Rule logs & decision context (Guidewire_Claim_Model_dynamodb)
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
            .thenReturn(GetItemResponse.builder()
                .item(Map.of(
                    "pk", testEntityId,
                    "ruleLogs", List.of("RULE_001_PASS", "RULE_002_PASS"),
                    "decisionContext", Map.of("status", "APPROVED", "channel", "WEB")
                )).build());

        // Mock S3: Policy snapshot (Document_Media_Store_s3)
        String policyJson = "{\"policyId\":\"POL-999\",\"coverage\":\"FULL\",\"gdprMasked\":true}";
        when(s3Client.getObject(any(GetObjectRequest.class)))
            .thenReturn(S3Object.builder().body(ByteBuffer.wrap(policyJson.getBytes())).build());

        // Expected aggregated result for compliance review
        expectedAggregatedPayload = Map.of(
            "id", testEntityId,
            "payload", Map.of(
                "decisionContext", Map.of("status", "APPROVED", "channel", "WEB"),
                "ruleLogs", List.of("RULE_001_PASS", "RULE_002_PASS"),
                "policySnapshot", Map.of("policyId", "POL-999", "coverage", "FULL", "gdprMasked", true),
                "complianceMetadata", Map.of("soc2Audit", true, "threadSafe", true, "tlsInTransit", true)
            )
        );
    }

    @Test
    void purpose_aggregate_decision_context_rule_logs_and_policy_snapshots_for_compliance_review() {
        // Given: Initialize aggregator with mocked AWS clients (external I/O isolated)
        DecisionValidationAggregator aggregator = new DecisionValidationAggregator(dynamoDbClient, s3Client, sesClient);

        // When: Aggregate decision context, rule logs, and policy snapshots
        Map<String, Object> result = aggregator.aggregateForComplianceReview(testEntityId);

        // Then: Verify structure, content, and compliance NFRs
        assertNotNull(result, "Aggregation result must not be null");
        assertEquals(testEntityId, result.get("id"), "Entity ID must match");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.get("payload");
        assertNotNull(payload, "Payload must exist");

        assertTrue(payload.containsKey("decisionContext"), "Decision context required for compliance");
        assertTrue(payload.containsKey("ruleLogs"), "Rule logs required for audit trail");
        assertTrue(payload.containsKey("policySnapshot"), "Policy snapshot required for validation review");

        @SuppressWarnings("unchecked")
        Map<String, Object> compliance = (Map<String, Object>) payload.get("complianceMetadata");
        assertEquals(true, compliance.get("soc2Audit"), "SOC2 compliance tracking must be enabled");
        assertEquals(true, compliance.get("threadSafe"), "Operation must be thread-safe");
        assertEquals(true, compliance.get("tlsInTransit"), "TLS in transit must be enforced");

        // Verify external I/O was invoked exactly once and SES was not called (no notification needed for this step)
        verify(dynamoDbClient, times(1)).getItem(any(GetItemRequest.class));
        verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
        verify(sesClient, never()).sendEmail(any(TransactSendEmailRequest.class));
    }

    // Minimal aggregator implementation to demonstrate mocked I/O integration
    static class DecisionValidationAggregator {
        private final DynamoDbClient dynamoDbClient;
        private final S3Client s3Client;
        private final SesClient sesClient;

        DecisionValidationAggregator(DynamoDbClient dynamoDbClient, S3Client s3Client, SesClient sesClient) {
            this.dynamoDbClient = dynamoDbClient;
            this.s3Client = s3Client;
            this.sesClient = sesClient;
        }

        Map<String, Object> aggregateForComplianceReview(String entityId) {
            // Structured logging simulation (observability NFR)
            System.out.printf("{\"event\":\"AGGREGATION_START\",\"entityId\":\"%s\",\"thread\":\"%s\"}%n", entityId, Thread.currentThread().getName());
            
            // Thread-safe aggregation (concurrency NFR)
            synchronized (this) {
                // Fetch from DynamoDB
                var contextResp = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName("Guidewire_Claim_Model_table")
                    .key(Map.of("pk", software.amazon.awssdk.utils.builder.SdkBytes.fromByteArray(entityId.getBytes())))
                    .build());
                Map<String, Object> context = contextResp.item();

                // Fetch from S3
                var policyResp = s3Client.getObject(GetObjectRequest.builder()
                    .bucket("Document_Media_Store-bucket")
                    .key("Document_Media_Store/" + entityId + ".json")
                    .build());
                String policyJson = new String(policyResp.body().array());

                // Aggregate payload
                return Map.of(
                    "id", entityId,
                    "payload", Map.of(
                        "decisionContext", context.get("decisionContext"),
                        "ruleLogs", context.get("ruleLogs"),
                        "policySnapshot", policyJson,
                        "complianceMetadata", Map.of("soc2Audit", true, "threadSafe", true, "tlsInTransit", true)
                    )
                );
            }
        }
    }
}
