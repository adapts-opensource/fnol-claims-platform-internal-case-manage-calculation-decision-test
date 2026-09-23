package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyKeyRequiredPerChannelSubmissionTest {

    @Mock
    private DynamoDbClient mockDynamoDbClient;

    @Mock
    private S3Client mockS3Client;

    private ClaimOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimOrchestrationService(mockDynamoDbClient, mockS3Client);
    }

    @Test
    void idempotencyKeyRequiredPerChannelSubmission() {
        String channelId = "WEB_PORTAL";
        Map<String, Object> payload = Map.of("claimId", "CLM-789", "status", "INITIATED");
        String idempotencyKey = null;

        assertThrows(IllegalArgumentException.class, () -> {
            orchestrationService.processChannelSubmission(channelId, payload, idempotencyKey);
        });

        verify(mockDynamoDbClient, never()).putItem(any(PutItemRequest.class));
        verify(mockS3Client, never()).putObject(any(PutObjectRequest.class));
    }

    static class ClaimOrchestrationService {
        private final DynamoDbClient dynamoDbClient;
        private final S3Client s3Client;

        ClaimOrchestrationService(DynamoDbClient dynamoDbClient, S3Client s3Client) {
            this.dynamoDbClient = dynamoDbClient;
            this.s3Client = s3Client;
        }

        void processChannelSubmission(String channelId, Map<String, Object> payload, String idempotencyKey) {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("Idempotency key is required per channel submission");
            }
            dynamoDbClient.putItem(PutItemRequest.builder().tableName("Claim Data Store_table").build());
            s3Client.putObject(PutObjectRequest.builder().bucketName("Document Management-bucket").build());
        }
    }
}
