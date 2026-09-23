package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Internal Case Management:calculation:decision feature.
 * Validates input criteria, business rules, decision points, and audit requirements.
 */
@ExtendWith(MockitoExtension.class)
class InternalCaseManagementCalculationDecisionInputCriteriaValidationTest {

    @Mock
    private AddressNormalizationService addressNormalizationService;

    @Mock
    private TaxonomyMappingService taxonomyMappingService;

    @Mock
    private CorrelationIdGenerator correlationIdGenerator;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private PolicyFormatValidator policyFormatValidator;

    @InjectMocks
    private CalculationDecisionService calculationDecisionService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Test
    void testValidStandardSubmission() {
        // Arrange
        Map<String, Object> payload = Map.of(
                "policyNumber", "FL-123456",
                "dateOfLoss", "2023-10-01",
                "causeOfLoss", "Fire",
                "reporterType", "Insured",
                "contactInfo", Map.of("email", "claimant@example.com"),
                "riskAddress", "123 Palm Ave, Miami, FL"
        );

        when(policyFormatValidator.validate(anyString())).thenReturn(true);
        when(addressNormalizationService.normalize(anyString())).thenReturn("123 Palm Ave, Miami, FL 33101");
        when(taxonomyMappingService.map(anyString())).thenReturn("Cause_Fire_Structured");
        when(correlationIdGenerator.generate()).thenReturn("corr-98765");
        when(eventPublisher.publish(eq("fnol_intake_validated"), any())).thenReturn(true);
        when(auditLogger.log(anyString(), anyString())).thenReturn("log-entry-id");

        // Act
        DecisionResult result = calculationDecisionService.processIntake(payload);

        // Assert
        assertTrue(result.isValid(), "Submission should be valid");
        assertEquals("123 Palm Ave, Miami, FL 33101", result.getNormalizedAddress());
        assertEquals("Cause_Fire_Structured", result.getInternalCause());
        assertNotNull(result.getCorrelationId());
        assertEquals("corr-98765", result.getCorrelationId());
        assertFalse(result.isFlaggedForManualReview());

        verify(policyFormatValidator).validate("FL-123456");
        verify(addressNormalizationService).normalize("123 Palm Ave, Miami, FL");
        verify(taxonomyMappingService).map("Fire");
        verify(correlationIdGenerator).generate();
        verify(eventPublisher).publish(eq("fnol_intake_validated"), any());
        verify(auditLogger).log(eq("validation_status"), eq("PASSED"));
    }

    @Test
    void testFutureDateOfLossRejected() {
        // Arrange
        Map<String, Object> payload = Map.of(
                "policyNumber", "FL-123456",
                "dateOfLoss", LocalDate.now().plusDays(1).format(DATE_FORMAT),
                "causeOfLoss", "Hail",
                "reporterType", "Insured",
                "contactInfo", Map.of("email", "test@example.com"),
                "riskAddress", "456 Ocean Dr"
        );

        // Act
        DecisionResult result = calculationDecisionService.processIntake(payload);

        // Assert
        assertFalse(result.isValid(), "Future date should cause validation failure");
        assertTrue(result.getErrors().containsKey("dateOfLoss"), "Error should be present for dateOfLoss");
        assertTrue(result.getErrors().get("dateOfLoss").contains("must not be in the future"), "Error message should indicate future date");

        verifyNoInteractions(addressNormalizationService, taxonomyMappingService);
        verify(auditLogger).log(eq("validation_status"), eq("FAILED"));
    }

    @Test
    void testInvalidPolicyFormatRejected() {
        // Arrange
        Map<String, Object> payload = Map.of(
                "policyNumber", "INVALID_POLICY",
                "dateOfLoss", "2023-09-15",
                "causeOfLoss", "Theft",
                "reporterType", "Broker",
                "contactInfo", Map.of("phone", "555-0199"),
                "riskAddress", "789 Bay St"
        );

        when(policyFormatValidator.validate(anyString())).thenReturn(false);

        // Act
        DecisionResult result = calculationDecisionService.processIntake(payload);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.getErrors().containsKey("policyNumber"), "Error should be present for policyNumber");
        assertTrue(result.getErrors().get("policyNumber").contains("must match format regex"), "Error should mention regex mismatch");

        verify(policyFormatValidator).validate("INVALID_POLICY");
        verifyNoInteractions(addressNormalizationService, taxonomyMappingService);
    }

    @Test
    void testMissingRequiredFieldsRejected() {
        // Arrange
        Map<String, Object> payload = Map.of(
                "policyNumber", "FL-123456",
                "dateOfLoss", "2023-08-20",
                "causeOfLoss", null,
                "reporterType", null,
                "contactInfo", Map.of(),
                "riskAddress", "100 River Rd"
        );

        // Act
        DecisionResult result = calculationDecisionService.processIntake(payload);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.getErrors().containsKey("causeOfLoss"));
        assertTrue(result.getErrors().containsKey("reporterType"));
        assertTrue(result.getErrors().containsKey("contactInformation"));

        verify(auditLogger).log(eq("validation_status"), eq("FAILED"));
    }

    @Test
    void testHighValueDamagesFlaggedForManualReview() {
        // Arrange
        Map<String, Object> payload = Map.of(
                "policyNumber", "FL-123456",
                "dateOfLoss", "2023-10-10",
                "causeOfLoss", "Fire",
                "reporterType", "Insured",
                "contactInfo", Map.of("email", "user@example.com"),
                "riskAddress", "555 Lake View",
                "estimatedDamages", 750000
        );

        when(policyFormatValidator.validate(anyString())).thenReturn(true);
        when(addressNormalizationService.normalize(anyString())).thenReturn("555 Lake View");
        when(taxonomyMappingService.map(anyString())).thenReturn("Cause_Fire");
        when(correlationIdGenerator.generate()).thenReturn("corr-manual-01");

        // Act
        DecisionResult result = calculationDecisionService.processIntake(payload);

        // Assert
        assertTrue(result.isValid());
        assertTrue(result.isFlaggedForManualReview(), "Damages > 500k should flag for manual review");
        assertEquals("MANUAL_REVIEW", result.getDecision());
    }

    @Test
    void testSpecialCharactersInAddressCausesNormalizationFailure() {
        // Arrange
        Map<String, Object> payload = Map.of(
                "policyNumber", "FL-123456",
                "dateOfLoss", "2023-07-01",
                "causeOfLoss", "Wind",
                "reporterType", "Insured",
                "contactInfo", Map.of("email", "fix@example.com"),
                "riskAddress", "123 Main St #A!!!@#"
        );

        when(policyFormatValidator.validate(anyString())).thenReturn(true);
        when(addressNormalizationService.normalize(anyString())).thenThrow(new RuntimeException("Normalization failed due to invalid characters"));

        // Act
        DecisionResult result = calculationDecisionService.processIntake(payload);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.getErrors().containsKey("riskAddress"));
        assertTrue(result.getErrors().get("riskAddress").contains("normalization"), "Error should relate to normalization failure");
    }

    @Test
    void testRealTimeProcessingLatency() {
        // Arrange
        Map<String, Object> payload = Map.of(
                "policyNumber", "FL-123456",
                "dateOfLoss", "2023-06-15",
                "causeOfLoss", "Collision",
                "reporterType", "Insured",
                "contactInfo", Map.of("email", "speed@example.com"),
                "riskAddress", "Fast Lane 1"
        );

        when(policyFormatValidator.validate(anyString())).thenReturn(true);
        when(addressNormalizationService.normalize(anyString())).thenReturn("Fast Lane 1");
        when(taxonomyMappingService.map(anyString())).thenReturn("Cause_Collision");
        when(correlationIdGenerator.generate()).thenReturn("corr-speed");

        // Act
        long startTime = System.nanoTime();
        DecisionResult result = calculationDecisionService.processIntake(payload);
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        // Assert
        assertTrue(result.isValid());
        assertTrue(durationMs < 2000, "Processing must complete within 2 seconds (took: " + durationMs + "ms)");
    }

    @Test
    void testExplainabilityAndAuditEvidence() {
        // Arrange
        Map<String, Object> payload = Map.of(
                "policyNumber", "FL-123456",
                "dateOfLoss", "2023-05-01",
                "causeOfLoss", "Vandalism",
                "reporterType", "Insured",
                "contactInfo", Map.of("email", "audit@example.com"),
                "riskAddress", "Audit Ave"
        );

        when(policyFormatValidator.validate(anyString())).thenReturn(true);
        when(addressNormalizationService.normalize(anyString())).thenReturn("Audit Ave");
        when(taxonomyMappingService.map(anyString())).thenReturn("Cause_Vandalism");
        when(correlationIdGenerator.generate()).thenReturn("corr-audit");
        when(auditLogger.logRawPayloadHash(anyString())).thenReturn("sha256-hash-xyz");

        // Act
        DecisionResult result = calculationDecisionService.processIntake(payload);

        // Assert
        assertTrue(result.isValid());
        verify(auditLogger).logRawPayloadHash(anyString());
        verify(auditLogger).log(eq("validation_timestamp"), anyString());
        verify(auditLogger).log(eq("processor_version"), anyString());
        verify(auditLogger).log(eq("fields_passed"), anyString());
    }
}
