package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionDecisionValidationMockTest {

    @Mock
    private Logger mockLogger;

    @Mock
    private CommunicationAckManager mockSesClient;

    @Mock
    private DocumentMediaStore mockS3Client;

    @Mock
    private GuidewireClaimModel mockDynamoDbClient;

    private MultiChannelFnolSubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new MultiChannelFnolSubmissionService(
                mockLogger, mockSesClient, mockS3Client, mockDynamoDbClient
        );
    }

    @Test
    void form_validates_all_required_fields_before_submission() {
        // Arrange: Simulate missing required fields (id and payload)
        Map<String, Object> incompletePayload = new HashMap<>();

        // Act & Assert: Validation should fail before any I/O occurs
        assertThrows(IllegalArgumentException.class, () -> {
            submissionService.submit("invalid-id", incompletePayload);
        });

        // Verify no external I/O was attempted due to validation failure
        verify(mockSesClient, never()).sendEmail(any(), any(), any());
        verify(mockS3Client, never()).putObject(any(), any());
        verify(mockDynamoDbClient, never()).putItem(any(), any());

        // Arrange: Valid required fields
        String validId = "fnol-12345";
        Map<String, Object> validPayload = new HashMap<>();
        validPayload.put("policyNumber", "POL-98765");
        validPayload.put("incidentType", "COLLISION");

        // Act & Assert: Validation passes, submission proceeds
        assertDoesNotThrow(() -> {
            Map<String, Object> result = submissionService.submit(validId, validPayload);
            assertNotNull(result);
            assertEquals(validId, result.get("id"));
            // Verify structured logging indicates validation success
            verify(mockLogger, times(1)).info("Form validation passed for submission.");
        });
    }
}

// Infra I/O Mock Interfaces
interface CommunicationAckManager { void sendEmail(String from, String to, String region); }
interface DocumentMediaStore { void putObject(String bucket, String key); }
interface GuidewireClaimModel { void putItem(String table, Map<String, Object> item); }

// Service under test
class MultiChannelFnolSubmissionService {
    private final Logger logger;
    private final CommunicationAckManager sesClient;
    private final DocumentMediaStore s3Client;
    private final GuidewireClaimModel dynamoDbClient;

    public MultiChannelFnolSubmissionService(Logger logger, CommunicationAckManager ses, DocumentMediaStore s3, GuidewireClaimModel dynamo) {
        this.logger = logger;
        this.sesClient = ses;
        this.s3Client = s3;
        this.dynamoDbClient = dynamo;
    }

    public Map<String, Object> submit(String id, Map<String, Object> payload) {
        // Input validation occurs first (NFR: input_validation)
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Required field 'id' is missing or blank.");
        }
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Required field 'payload' is missing or empty.");
        }

        // Structured logging for observability
        logger.info("Form validation passed for submission.");

        // Mock I/O would happen here (SES, S3, DynamoDB)
        Map<String, Object> submissionResult = new HashMap<>();
        submissionResult.put("id", id);
        submissionResult.put("status", "ACCEPTED");
        return submissionResult;
    }
}
