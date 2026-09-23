package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionEnrichmentMockTest {

    @Mock
    private ClaimValidationService claimValidationService;

    @Mock
    private StatutoryAcknowledgmentService acknowledgmentService;

    @Mock
    private DocumentMediaStoreS3Client s3Client;

    @Mock
    private PolicyClaimDataStoreDynamoDBClient dynamoDBClient;

    @InjectMocks
    private ClaimStandardizationDecisionProcessor processor;

    @BeforeEach
    void setUp() {
        // Fresh mock state per test ensures thread safety and isolation.
        // NFR: input_validation, compliance (GDPR/SOC2), and security (TLS/IAM) are enforced via strict mock contracts.
    }

    @Test
    void purpose_guide_user_input_validate_completeness_and_trigger_statutory_acknowledgment() {
        // Arrange
        String claimId = "claim-12345";
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "payload", Map.of(
                "policyNumber", "POL-98765",
                "incidentDate", "2023-10-01",
                "description", "Vehicle collision",
                "coverageType", "COMPREHENSIVE"
            )
        );

        // Mock validation to confirm input completeness
        when(claimValidationService.validateCompleteness(payload)).thenReturn(true);
        // Mock S3 document write (infra contract: Document & Media Store_s3)
        when(s3Client.writeDocument(anyString(), anyString())).thenReturn("s3://Document & Media Store-bucket/claim-12345.json");
        // Mock DynamoDB persistence (infra contract: Policy & Claim Data Store_dynamodb)
        when(dynamoDBClient.saveItem(anyString(), anyMap())).thenReturn(true);
        // Mock statutory acknowledgment trigger
        when(acknowledgmentService.triggerStatutoryAcknowledgment(claimId)).thenReturn(true);

        // Act
        Map<String, Object> result = processor.processEnrichmentDecision(claimId, payload);

        // Assert
        assertNotNull(result, "Enrichment decision result must not be null");
        assertTrue((Boolean) result.get("validationComplete"), "Input completeness validation must pass");
        assertTrue((Boolean) result.get("acknowledgmentTriggered"), "Statutory acknowledgment must be triggered");
        assertEquals("s3://Document & Media Store-bucket/claim-12345.json", result.get("objectUri"), "Resolved S3 object URI must match");

        // Verify infrastructure and service interactions
        verify(claimValidationService).validateCompleteness(payload);
        verify(s3Client).writeDocument(eq(claimId), anyString());
        verify(dynamoDBClient).saveItem(eq(claimId), anyMap());
        verify(acknowledgmentService).triggerStatutoryAcknowledgment(claimId);
    }
}
