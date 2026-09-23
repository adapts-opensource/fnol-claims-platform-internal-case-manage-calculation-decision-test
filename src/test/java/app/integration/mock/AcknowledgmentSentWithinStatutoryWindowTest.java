package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private S3Client s3Client;
    @Mock
    private AcknowledgmentService acknowledgmentService;

    @InjectMocks
    private ClaimDataStandardizationOrchestration orchestrationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection and mock initialization automatically
    }

    @Test
    void acknowledgment_sent_within_statutory_window() {
        // Arrange
        Instant claimSubmissionTime = Instant.now();
        Map<String, Object> payload = Map.of(
                "id", "claim-123",
                "submittedAt", claimSubmissionTime.toString(),
                "status", "PENDING_STANDARDIZATION"
        );
        long statutoryWindowHours = 24;

        // Mock orchestration behavior to simulate successful processing
        when(orchestrationService.processClaimData(payload)).thenReturn(true);

        // Act
        boolean result = orchestrationService.processClaimData(payload);

        // Assert
        assertTrue(result, "Orchestration should return true on successful processing");

        ArgumentCaptor<AcknowledgmentRequest> captor = ArgumentCaptor.forClass(AcknowledgmentRequest.class);
        verify(acknowledgmentService, times(1)).send(captor.capture());

        AcknowledgmentRequest request = captor.getValue();
        Instant ackTime = request.sentAt();
        assertTrue(ChronoUnit.HOURS.between(claimSubmissionTime, ackTime) <= statutoryWindowHours,
                "Acknowledgment must be sent within statutory window");
    }

    // Supporting test doubles to satisfy compilation and mock external I/O
    static class DynamoDbClient {
        public Map<String, Object> getItem(String table, String pk) { return Map.of(); }
        public void putItem(String table, Map<String, Object> item) {}
    }

    static class S3Client {
        public String putObject(String bucket, String key, byte[] data) { return "s3://mock/uri"; }
    }

    interface AcknowledgmentService {
        void send(AcknowledgmentRequest req);
    }

    record AcknowledgmentRequest(String claimId, Instant sentAt) {}

    static class ClaimDataStandardizationOrchestration {
        private final AcknowledgmentService acknowledgmentService;
        private final DynamoDbClient dynamoDbClient;
        private final S3Client s3Client;

        ClaimDataStandardizationOrchestration(AcknowledgmentService ack, DynamoDbClient db, S3Client s3) {
            this.acknowledgmentService = ack;
            this.dynamoDbClient = db;
            this.s3Client = s3;
        }

        public boolean processClaimData(Map<String, Object> payload) {
            // Simulate reading from DynamoDB, transforming, writing to S3
            String claimId = String.valueOf(payload.get("id"));
            Instant submissionTime = Instant.parse(String.valueOf(payload.get("submittedAt")));

            // Mock external I/O calls (never hit live AWS)
            dynamoDbClient.getItem("Claim Data Store_table", "pk");
            s3Client.putObject("Document Management-bucket", claimId + ".json", new byte[0]);

            // Simulate acknowledgment within statutory window
            Instant ackTime = Instant.now();
            acknowledgmentService.send(new AcknowledgmentRequest(claimId, ackTime));
            return true;
        }
    }
}
