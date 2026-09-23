package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationOrchestrationIntegrationMockTest {

    @Mock
    private DynamoDBClient dynamoDBClient;

    @Mock
    private S3Client s3Client;

    private ClaimStandardizationOrchestrationService orchestrationService;

    @Captor
    private ArgumentCaptor<PutItemRequest> putItemCaptor;

    @Captor
    private ArgumentCaptor<PutObjectRequest> putObjectCaptor;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimStandardizationOrchestrationServiceImpl(dynamoDBClient, s3Client);
    }

    @Test
    void system_accepts_valid_inputs_and_creates_standardized_shell() {
        // Arrange: Valid inputs conforming to data model and validation rules
        String validId = "CLM-TEST-001";
        Map<String, Object> validPayload = new HashMap<>();
        validPayload.put("claimType", "AUTO");
        validPayload.put("status", "INTAKE");
        validPayload.put("timestamp", "2023-10-27T10:00:00Z");
        validPayload.put("policyNumber", "POL-12345");

        // Act: Trigger orchestration
        orchestrationService.processClaimData(validId, validPayload);

        // Assert: Verify DynamoDB shell creation
        verify(dynamoDBClient, times(1)).putItem(putItemCaptor.capture());
        PutItemRequest capturedPutItem = putItemCaptor.getValue();
        assertEquals("Claim Data Store_table", capturedPutItem.tableName());
        Map<String, AttributeValue> item = capturedPutItem.item();
        assertNotNull(item, "DynamoDB item must be created");
        assertEquals(validId, item.get("pk").s(), "Partition key must match input ID");
        assertEquals("standardized_shell", item.get("type").s(), "State type must be standardized_shell");
        assertNotNull(item.get("payload").m(), "Payload map must be serialized");

        // Assert: Verify S3 document storage
        verify(s3Client, times(1)).putObject(putObjectCaptor.capture());
        PutObjectRequest capturedPutObject = putObjectCaptor.getValue();
        assertEquals("Document Management-bucket", capturedPutObject.bucket());
        assertTrue(capturedPutObject.key().startsWith("Document Management/"), "Object key must follow pattern");

        // NFR: Input validation & Security contract compliance
        assertDoesNotThrow(() -> orchestrationService.processClaimData(validId, validPayload),
                "System should accept valid inputs without throwing validation exceptions");
    }

    // Service interface and implementation for orchestration logic
    interface ClaimStandardizationOrchestrationService {
        void processClaimData(String id, Map<String, Object> payload);
    }

    static class ClaimStandardizationOrchestrationServiceImpl implements ClaimStandardizationOrchestrationService {
        private final DynamoDBClient dynamoDBClient;
        private final S3Client s3Client;

        ClaimStandardizationOrchestrationServiceImpl(DynamoDBClient dynamoDBClient, S3Client s3Client) {
            this.dynamoDBClient = dynamoDBClient;
            this.s3Client = s3Client;
        }

        @Override
        public void processClaimData(String id, Map<String, Object> payload) {
            // Validate inputs (NFR: input_validation)
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Claim ID must not be blank");
            }
            if (payload == null || payload.isEmpty()) {
                throw new IllegalArgumentException("Payload must not be null or empty");
            }

            // Create standardized shell in DynamoDB
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("pk", AttributeValue.builder().s(id).build());
            item.put("type", AttributeValue.builder().s("standardized_shell").build());
            item.put("payload", AttributeValue.builder().m(payload).build());

            dynamoDBClient.putItem(PutItemRequest.builder()
                    .tableName("Claim Data Store_table")
                    .item(item)
                    .build());

            // Persist document payload to S3
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket("Document Management-bucket")
                    .key("Document Management/" + id + ".json")
                    .build());
        }
    }
}
