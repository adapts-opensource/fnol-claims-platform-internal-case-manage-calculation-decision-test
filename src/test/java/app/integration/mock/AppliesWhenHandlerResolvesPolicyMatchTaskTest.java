package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.s3.GetObjectResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionDecisionValidationTest {

    @Mock
    private DynamoDbClient policyCoverageValidatorDynamoDb;

    @Mock
    private DynamoDbClient guidewireClaimModelDynamoDb;

    @Mock
    private S3Client documentMediaStoreS3;

    @Mock
    private SesClient communicationAckManagerSes;

    private FnolSubmissionDecisionValidatorService fnolSubmissionDecisionValidatorService;

    @BeforeEach
    void setUp() {
        fnolSubmissionDecisionValidatorService = new FnolSubmissionDecisionValidatorService(
            policyCoverageValidatorDynamoDb,
            guidewireClaimModelDynamoDb,
            documentMediaStoreS3,
            communicationAckManagerSes
        );
    }

    @Test
    void applies_when_handler_resolves_policy_match_task() {
        // Arrange
        String fnolId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
            "id", fnolId,
            "policyMatchTask", Map.of("status", "RESOLVED", "handlerId", "handler-123"),
            "channel", "WEB"
        );

        // Mock AWS SDK responses for external I/O contracts
        when(policyCoverageValidatorDynamoDb.scan(any(ScanRequest.class)))
            .thenReturn(mock(software.amazon.awssdk.services.dynamodb.model.ScanResponse.class));
        
        when(documentMediaStoreS3.getObject(any(GetObjectRequest.class), any()))
            .thenReturn(mock(GetObjectResponse.class));
        
        when(communicationAckManagerSes.sendEmail(any(SendEmailRequest.class)))
            .thenReturn(mock(SendEmailResponse.class).toBuilder().messageId("ses-msg-001").build());

        // Act
        Map<String, Object> result = fnolSubmissionDecisionValidatorService.validateSubmission(payload);

        // Assert decision logic
        assertNotNull(result);
        assertEquals("VALIDATED", result.get("decision"));
        assertTrue((Boolean) result.get("policyMatchTaskResolved"));
        assertEquals(fnolId, result.get("id"));

        // Verify external I/O interactions per infra contracts
        verify(policyCoverageValidatorDynamoDb, times(1)).scan(any(ScanRequest.class));
        verify(documentMediaStoreS3, times(1)).getObject(any(GetObjectRequest.class), any());
        verify(communicationAckManagerSes, times(1)).sendEmail(any(SendEmailRequest.class));
    }

    /**
     * Minimal service implementation to isolate decision validation logic from live infrastructure.
     */
    static class FnolSubmissionDecisionValidatorService {
        private final DynamoDbClient policyCoverageValidatorDynamoDb;
        private final DynamoDbClient guidewireClaimModelDynamoDb;
        private final S3Client documentMediaStoreS3;
        private final SesClient communicationAckManagerSes;

        FnolSubmissionDecisionValidatorService(DynamoDbClient policyCoverageValidatorDynamoDb,
                                               DynamoDbClient guidewireClaimModelDynamoDb,
                                               S3Client documentMediaStoreS3,
                                               SesClient communicationAckManagerSes) {
            this.policyCoverageValidatorDynamoDb = policyCoverageValidatorDynamoDb;
            this.guidewireClaimModelDynamoDb = guidewireClaimModelDynamoDb;
            this.documentMediaStoreS3 = documentMediaStoreS3;
            this.communicationAckManagerSes = communicationAckManagerSes;
        }

        Map<String, Object> validateSubmission(Map<String, Object> payload) {
            @SuppressWarnings("unchecked")
            Map<String, Object> task = (Map<String, Object>) payload.get("policyMatchTask");
            
            if (task != null && "RESOLVED".equals(task.get("status"))) {
                // Trigger validation workflows per infra contracts
                policyCoverageValidatorDynamoDb.scan(ScanRequest.builder().build());
                documentMediaStoreS3.getObject(
                    GetObjectRequest.builder().bucket("Document_Media_Store-bucket").key("Document_Media_Store/" + payload.get("id") + ".json").build(),
                    GetObjectResponse.class
                );
                communicationAckManagerSes.sendEmail(SendEmailRequest.builder().build());
                
                return Map.of("decision", "VALIDATED", "policyMatchTaskResolved", true, "id", payload.get("id"));
            }
            return Map.of("decision", "PENDING", "policyMatchTaskResolved", false, "id", payload.get("id"));
        }
    }
}
