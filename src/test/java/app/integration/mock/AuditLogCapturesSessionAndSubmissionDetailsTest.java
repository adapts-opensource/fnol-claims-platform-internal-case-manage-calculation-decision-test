package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization: Enrichment: Decision
 * Verifies audit logging captures session and submission details.
 */
@ExtendWith(MockitoExtension.class)
public class AuditLogCapturesSessionAndSubmissionDetailsTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private ClaimEnrichmentDecisionProcessor claimEnrichmentDecisionProcessor;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and injection
    }

    @Test
    void audit_log_captures_session_and_submission_details() {
        // Arrange
        String sessionId = "sess-7f8a9b0c";
        Map<String, Object> submissionDetails = Map.of(
                "id", "claim-std-001",
                "payload", Map.of("claimType", "FNOL", "status", "PENDING")
        );

        when(dynamoDbClient.putItem(any())).thenReturn(null);
        when(s3Client.putObject(any(), any())).thenReturn(null);
        when(auditLogger.capture(anyString(), anyString(), anyMap())).thenReturn(true);

        // Act
        boolean result = claimEnrichmentDecisionProcessor.processEnrichment(sessionId, submissionDetails);

        // Assert
        assertTrue(result, "Enrichment decision processing should succeed");
        
        ArgumentCaptor<String> sessionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(auditLogger).capture(eq("CLAIM_STANDARDIZATION_DECISION"), sessionCaptor.capture(), payloadCaptor.capture());
        assertEquals(sessionId, sessionCaptor.getValue());
        assertEquals(submissionDetails, payloadCaptor.getValue());
        verifyNoMoreInteractions(auditLogger);
    }
}

// Minimal production-like processor for test injection
class ClaimEnrichmentDecisionProcessor {
    private final DynamoDbClient dynamoDbClient;
    private final S3Client s3Client;
    private final AuditLogger auditLogger;

    ClaimEnrichmentDecisionProcessor(DynamoDbClient dynamoDbClient, S3Client s3Client, AuditLogger auditLogger) {
        this.dynamoDbClient = dynamoDbClient;
        this.s3Client = s3Client;
        this.auditLogger = auditLogger;
    }

    boolean processEnrichment(String sessionId, Map<String, Object> submissionDetails) {
        dynamoDbClient.putItem(null);
        s3Client.putObject(null, null);
        return auditLogger.capture("CLAIM_STANDARDIZATION_DECISION", sessionId, submissionDetails);
    }
}

interface AuditLogger {
    boolean capture(String feature, String sessionId, Map<String, Object> submissionDetails);
}
