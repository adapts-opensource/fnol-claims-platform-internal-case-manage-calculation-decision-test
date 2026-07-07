package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RejectedSubmissionsReturnClearFieldLevelErrors {

    @Mock
    private ClaimEnrichmentValidationService validationService;

    @Mock
    private DocumentStoreS3Client s3Client;

    @Mock
    private PolicyClaimDataDynamoDbClient dynamoDbClient;

    @InjectMocks
    private ClaimStandardizationProcessor processor;

    @BeforeEach
    void setUp() {
        // Verify mock injection and initialize shared test state
        assertNotNull(validationService);
        assertNotNull(s3Client);
        assertNotNull(dynamoDbClient);
    }

    @Test
    void rejected_submissions_return_clear_field_level_errors() {
        // Arrange
        String claimId = "CLM-STD-001";
        Map<String, Object> invalidPayload = Map.of(
            "policyNumber", "",
            "dateOfLoss", "not-a-date",
            "insuredName", null
        );

        List<FieldError> fieldErrors = List.of(
            new FieldError("policyNumber", "Policy number is required and cannot be blank"),
            new FieldError("dateOfLoss", "Date of loss must follow YYYY-MM-DD format"),
            new FieldError("insuredName", "Insured name cannot be null")
        );

        ValidationResult validationResult = new ValidationResult(
            claimId,
            "REJECTED",
            fieldErrors
        );

        when(validationService.enrichAndValidate(claimId, invalidPayload)).thenReturn(validationResult);
        when(s3Client.storeDocument(anyString(), anyString())).thenReturn("s3://Document%20%26%20Media%20Store-bucket/CLM-STD-001.json");
        when(dynamoDbClient.putItem(anyString(), anyMap())).thenReturn(Map.of("pk", claimId));

        // Act
        ValidationResponse response = processor.processClaimSubmission(claimId, invalidPayload);

        // Assert
        assertNotNull(response);
        assertEquals("REJECTED", response.status());
        assertEquals(3, response.errors().size());
        assertEquals("policyNumber", response.errors().get(0).field());
        assertEquals("Date of loss must follow YYYY-MM-DD format", response.errors().get(1).message());

        // Verify external I/O interactions were triggered correctly
        verify(validationService, times(1)).enrichAndValidate(claimId, invalidPayload);
        verify(s3Client, times(1)).storeDocument(eq(claimId), anyString());
        verify(dynamoDbClient, times(1)).putItem(eq("Policy & Claim Data Store_table"), anyMap());
    }

    // Test doubles and typed models for isolation
    interface ClaimEnrichmentValidationService {
        ValidationResult enrichAndValidate(String claimId, Map<String, Object> payload);
    }

    interface DocumentStoreS3Client {
        String storeDocument(String bucketName, String objectKey);
    }

    interface PolicyClaimDataDynamoDbClient {
        Map<String, Object> putItem(String tableName, Map<String, Object> item);
    }

    class ClaimStandardizationProcessor {
        private final ClaimEnrichmentValidationService validationService;
        private final DocumentStoreS3Client s3Client;
        private final PolicyClaimDataDynamoDbClient dynamoDbClient;

        ClaimStandardizationProcessor(
            ClaimEnrichmentValidationService validationService,
            DocumentStoreS3Client s3Client,
            PolicyClaimDataDynamoDbClient dynamoDbClient) {
            this.validationService = validationService;
            this.s3Client = s3Client;
            this.dynamoDbClient = dynamoDbClient;
        }

        ValidationResponse processClaimSubmission(String claimId, Map<String, Object> payload) {
            ValidationResult result = validationService.enrichAndValidate(claimId, payload);
            s3Client.storeDocument("Document & Media Store-bucket", claimId + ".json");
            dynamoDbClient.putItem("Policy & Claim Data Store_table", Map.of("id", claimId, "payload", payload));
            return new ValidationResponse(result.id(), result.status(), result.errors());
        }
    }

    record FieldError(String field, String message) {}
    record ValidationResult(String id, String status, List<FieldError> errors) {}
    record ValidationResponse(String id, String status, List<FieldError> errors) {}
}
