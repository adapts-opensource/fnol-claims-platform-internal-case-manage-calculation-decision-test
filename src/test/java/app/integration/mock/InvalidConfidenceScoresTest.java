package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Validates orchestration rejection of FNOL submissions with invalid confidence scores.
 * Mocks external I/O (S3, DynamoDB, SES) to ensure no live network calls during validation.
 */
@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionOrchestrationValidationTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private SesClient sesClient;

    @InjectMocks
    private MultiChannelFnolSubmissionOrchestrator orchestrator;

    @Test
    void invalid_confidence_scores() {
        // Arrange: Negative confidence score
        Map<String, Object> payloadNegative = Map.of("channelId", "mobile_001", "confidenceScores", Map.of("collision", -0.2));
        assertThrows(IllegalArgumentException.class, () -> orchestrator.processSubmission(payloadNegative));

        // Arrange: Confidence score exceeds 1.0
        Map<String, Object> payloadExceeds = Map.of("channelId", "web_002", "confidenceScores", Map.of("collision", 1.5));
        assertThrows(IllegalArgumentException.class, () -> orchestrator.processSubmission(payloadExceeds));

        // Arrange: Missing confidence score
        Map<String, Object> payloadMissing = Map.of("channelId", "call_center_003", "confidenceScores", Map.of("collision", null));
        assertThrows(IllegalArgumentException.class, () -> orchestrator.processSubmission(payloadMissing));

        // Arrange: Empty confidence scores collection
        Map<String, Object> payloadEmpty = Map.of("channelId", "mobile_004", "confidenceScores", List.of());
        assertThrows(IllegalArgumentException.class, () -> orchestrator.processSubmission(payloadEmpty));
    }
}
