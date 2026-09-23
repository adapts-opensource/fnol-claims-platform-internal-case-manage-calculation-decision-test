package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditTrailCapturesMatchDecisionTest {

    @Mock
    private S3Client auditDiaryStoreS3;

    @Mock
    private DynamoDbClient rulesEngineDynamoDb;

    private ClaimDataTransformationService transformationService;

    private static final String BUCKET_NAME = "AuditDiaryStore-bucket";
    private static final String CLAIM_ID = "claim-12345";
    private static final String MATCH_DECISION = "MATCH";
    private static final String PK = "pk";

    @BeforeEach
    void setUp() {
        transformationService = new ClaimDataTransformationService(auditDiaryStoreS3, rulesEngineDynamoDb, BUCKET_NAME);
    }

    @Test
    void audit_trail_captures_match_decision() {
        // Arrange: Mock DynamoDB response with a match decision
        Map<String, AttributeValue> decisionItem = new HashMap<>();
        decisionItem.put(PK, AttributeValue.builder().s(CLAIM_ID).build());
        decisionItem.put("match_decision", AttributeValue.builder().s(MATCH_DECISION).build());

        when(rulesEngineDynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(decisionItem).build());

        ArgumentCaptor<PutObjectRequest> auditRequestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);

        // Act: Trigger transformation and audit trail capture
        transformationService.transformAndAudit(CLAIM_ID, new HashMap<>());

        // Assert: Verify DynamoDB decision service was queried
        verify(rulesEngineDynamoDb, times(1)).getItem(any(GetItemRequest.class));

        // Assert: Verify S3 audit diary was written with correct parameters
        verify(auditDiaryStoreS3, times(1)).putObject(auditRequestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest capturedRequest = auditRequestCaptor.getValue();
        assertEquals(BUCKET_NAME, capturedRequest.bucket());
        assertTrue(capturedRequest.key().startsWith("AuditDiaryStore/"));

        // Assert: Verify match decision is correctly captured in the audit payload
        String payloadContent = new String(capturedRequest.body().array(), StandardCharsets.UTF_8);
        assertTrue(payloadContent.contains("match_decision"), "Audit payload must contain match_decision field");
        assertTrue(payloadContent.contains(MATCH_DECISION), "Audit payload must capture the exact match decision");
        assertTrue(payloadContent.contains(CLAIM_ID), "Audit payload must reference the claim ID");
        assertTrue(payloadContent.contains("CAPTURED"), "Audit payload must indicate successful capture status");
    }

    // Minimal service implementation to orchestrate the transformation and audit flow
    static class ClaimDataTransformationService {
        private final S3Client s3Client;
        private final DynamoDbClient dynamoDbClient;
        private final String bucketName;

        public ClaimDataTransformationService(S3Client s3Client, DynamoDbClient dynamoDbClient, String bucketName) {
            this.s3Client = s3Client;
            this.dynamoDbClient = dynamoDbClient;
            this.bucketName = bucketName;
        }

        public void transformAndAudit(String claimId, Map<String, Object> payload) {
            // 1. Fetch decision from Rules Engine (DynamoDB)
            GetItemRequest request = GetItemRequest.builder()
                    .tableName("RulesEngineDecisionService_table")
                    .key(Map.of(PK, AttributeValue.builder().s(claimId).build()))
                    .build();
            var response = dynamoDbClient.getItem(request);
            String matchDecision = response.item().get("match_decision").s();

            // 2. Construct audit trail payload
            String auditPayload = String.format(
                    "{\"claim_id\":\"%s\",\"timestamp\":\"%s\",\"match_decision\":\"%s\",\"status\":\"CAPTURED\"}",
                    claimId, Instant.now().toString(), matchDecision
            );

            // 3. Write to Audit Diary Store (S3)
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key("AuditDiaryStore/" + claimId + ".json")
                            .build(),
                    RequestBody.fromBytes(auditPayload.getBytes(StandardCharsets.UTF_8))
            );
        }
    }
}
