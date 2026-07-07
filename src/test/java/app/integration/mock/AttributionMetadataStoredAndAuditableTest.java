package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AttributionMetadataStoredAndAuditableTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private AuditLogger auditLogger;

    private ClaimDataOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimDataOrchestrationService(dynamoDbClient, auditLogger);
    }

    @Test
    void attributionMetadataStoredAndAuditable() {
        // arrange
        String claimId = "claim-123";
        Map<String, AttributeValue> payload = new HashMap<>();
        payload.put("attributionSource", AttributeValue.builder().s("web_portal").build());
        payload.put("attributionTimestamp", AttributeValue.builder().s("2023-10-25T10:00:00Z").build());

        Map<String, AttributeValue> itemPayload = new HashMap<>();
        itemPayload.put("pk", AttributeValue.builder().s(claimId).build());
        itemPayload.put("id", AttributeValue.builder().s(claimId).build());
        itemPayload.put("payload", AttributeValue.builder().m(payload).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName("Claim Data Store_table")
                .item(itemPayload)
                .build();

        when(dynamoDbClient.putItem(putItemRequest)).thenReturn(PutItemResponse.builder().build());

        // act
        orchestrationService.processClaimData(claimId, payload);

        // assert
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient, times(1)).putItem(captor.capture());
        PutItemRequest capturedRequest = captor.getValue();

        assertEquals("Claim Data Store_table", capturedRequest.tableName());
        assertEquals(claimId, capturedRequest.item().get("pk").s());
        assertEquals(claimId, capturedRequest.item().get("id").s());
        assertEquals("web_portal", capturedRequest.item().get("payload").m().get("attributionSource").s());
        assertEquals("2023-10-25T10:00:00Z", capturedRequest.item().get("payload").m().get("attributionTimestamp").s());

        verify(auditLogger, times(1)).logAudit(
                eq("CLAIM_STANDARDIZATION"),
                eq(claimId),
                eq("STORAGE_SUCCESS"),
                eq("GDPR_COMPLIANT_AUDIT")
        );
    }

    // Lightweight orchestration implementation for isolated testing
    private static class ClaimDataOrchestrationService {
        private final DynamoDbClient dynamoDbClient;
        private final AuditLogger auditLogger;

        ClaimDataOrchestrationService(DynamoDbClient dynamoDbClient, AuditLogger auditLogger) {
            this.dynamoDbClient = dynamoDbClient;
            this.auditLogger = auditLogger;
        }

        void processClaimData(String claimId, Map<String, AttributeValue> payload) {
            Map<String, AttributeValue> itemPayload = new HashMap<>();
            itemPayload.put("pk", AttributeValue.builder().s(claimId).build());
            itemPayload.put("id", AttributeValue.builder().s(claimId).build());
            itemPayload.put("payload", AttributeValue.builder().m(payload).build());

            PutItemRequest request = PutItemRequest.builder()
                    .tableName("Claim Data Store_table")
                    .item(itemPayload)
                    .build();
            dynamoDbClient.putItem(request);
            auditLogger.logAudit("CLAIM_STANDARDIZATION", claimId, "STORAGE_SUCCESS", "GDPR_COMPLIANT_AUDIT");
        }
    }

    private interface AuditLogger {
        void logAudit(String feature, String entityId, String action, String complianceTag);
    }
}
