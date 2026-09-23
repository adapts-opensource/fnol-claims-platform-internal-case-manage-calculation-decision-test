package app.integration.mock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * JUnit 5 test class with @Test methods for Insured Engagement & Tracking:decision:transformation.
 * Verifies input criteria transformation, external I/O mocking, and NFR compliance (PII masking, validation).
 */
@ExtendWith(MockitoExtension.class)
class InsuredEngagementTransformationMockTest {

    @Mock
    private DynamoDBClient mockDynamoDBClient;
    @Mock
    private SESClient mockSESClient;
    @Mock
    private S3Client mockS3Client;

    private InsuredEngagementTransformationService transformationService;

    @BeforeEach
    void setUp() {
        // Initialize service under test with mocked external dependencies
        transformationService = new InsuredEngagementTransformationService(
            mockDynamoDBClient, mockSESClient, mockS3Client
        );
    }

    @Test
    void input_criteria_policy_number_optional_risk_address_named_insured_dob_ssn_last_4_dol_cause_of_loss_occupancy_type() {
        // Arrange: Input criteria per feature specification
        String policyNumber = "POL-2024-8891"; // Optional field, provided for test
        String riskAddress = "789 Pine Rd, Denver, CO 80202";
        String namedInsured = "Alex Johnson";
        String dob = "1988-11-30";
        String ssnLast4 = "4321";
        String dol = "2024-05-10";
        String causeOfLoss = "Hail Damage";
        String occupancyType = "Single Family Dwelling";

        // Mock external I/O: DynamoDB, SES, S3 (never calls live AWS)
        doNothing().when(mockDynamoDBClient).putItem(anyString(), any(Map.class));
        doNothing().when(mockSESClient).sendEmail(anyString(), anyList(), anyString());
        when(mockS3Client.storeDocument(anyString(), anyString())).thenReturn("s3://newco-docs/engagement-8891.json");

        // Act: Execute transformation logic
        TransformationOutcome outcome = transformationService.transformEngagementCriteria(
            policyNumber, riskAddress, namedInsured, dob, ssnLast4, dol, causeOfLoss, occupancyType
        );

        // Assert: Validate transformation results and NFR compliance
        assertNotNull(outcome, "Outcome must not be null after transformation");
        assertTrue(outcome.isSuccess(), "Transformation should succeed with valid input criteria");
        assertEquals("ENG-TRANSFORMED-8891", outcome.getEngagementId(), "Engagement ID must be generated");
        assertFalse(outcome.isContainsPII(), "SSN/DOB must be masked per GDPR/SOC2 compliance");
        assertTrue(outcome.getInputValidationPassed(), "Input validation must pass for all required fields");

        // Verify: External I/O interactions (mocked, thread-safe, least-privilege IAM implied)
        verify(mockDynamoDBClient, times(1)).putItem(eq("ReserveLineTable"), any(Map.class));
        verify(mockSESClient, times(1)).sendEmail(eq("noreply@newco-insurance.com"), anyList(), eq("Engagement Transformed"));
        verify(mockS3Client, times(1)).storeDocument(eq("engagement-8891.json"), anyString());
    }
}
