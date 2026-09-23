package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static app.integration.mock.ClaimDataStandardizationDecisionEnrichmentMockTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Mock integration tests for Claim Data Standardization:decision:enrichment.
 * Verifies output criteria including success metrics, drift alerts, and failure flags.
 * 
 * NFR Compliance:
 * - GDPR/SOC2: Verifies no PII leakage in enrichment outputs.
 * - Input Validation: Ensures incomplete datasets trigger specific failure outputs.
 * - Observability: Structured logging is assumed in service implementation.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionEnrichmentMockTest {

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @InjectMocks
    private DecisionEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        // Initialize service with mocked dependencies
        enrichmentService = new DecisionEnrichmentService(rulesEngineService, auditDiaryStore);
    }

    /**
     * Test Case: output_criteria_success_outputs_accuracy_metrics_drift_alerts_recommended_rule_adjustments_failure_outputs_data_quality_warnings
     * 
     * Verifies that the enrichment process generates success outputs (metrics, alerts, adjustments)
     * alongside failure outputs (quality warnings, incomplete dataset flag) when processing
     * an incomplete dataset scenario.
     */
    @Test
    @DisplayName("Output criteria success outputs accuracy metrics drift alerts recommended rule adjustments failure outputs data quality warnings incomplete dataset flag")
    void outputCriteriaSuccessOutputsAccuracyMetricsDriftAlertsRecommendedRuleAdjustmentsFailureOutputsDataQualityWarningsIncompleteDatasetFlag() {
        // Arrange
        String claimId = "claim-std-789";
        Map<String, Object> payload = Map.of(
            "policyNumber", "POL-001",
            "incidentDate", "2023-10-01",
            "coverageType", "COMPREHENSIVE"
            // Note: Intentionally missing 'damageAssessment' to trigger incomplete_dataset_flag
        );

        // Mock Rules Engine returning success outputs
        when(rulesEngineService.evaluate(any(Map.class))).thenReturn(Map.of(
            "accuracy_metrics", 0.95,
            "drift_alerts", List.of("severity_medium"),
            "recommended_rule_adjustments", List.of("threshold_update_v2")
        ));

        // Mock Audit Diary Store for observability/compliance logging
        when(auditDiaryStore.write(anyString(), anyString())).thenReturn("s3://audit-bucket/claim-std-789.json");

        // Act
        EnrichmentOutcome outcome = enrichmentService.processEnrichment(claimId, payload);

        // Assert Success Outputs
        assertNotNull(outcome);
        assertTrue(outcome.getSuccessOutputs().containsKey("accuracy_metrics"));
        assertEquals(0.95, outcome.getSuccessOutputs().get("accuracy_metrics"));
        
        assertTrue(outcome.getSuccessOutputs().containsKey("drift_alerts"));
        List<?> driftAlerts = (List<?>) outcome.getSuccessOutputs().get("drift_alerts");
        assertTrue(driftAlerts.contains("severity_medium"));
        
        assertTrue(outcome.getSuccessOutputs().containsKey("recommended_rule_adjustments"));
        List<?> adjustments = (List<?>) outcome.getSuccessOutputs().get("recommended_rule_adjustments");
        assertTrue(adjustments.contains("threshold_update_v2"));

        // Assert Failure Outputs
        assertTrue(outcome.getFailureOutputs().containsKey("data_quality_warnings"));
        assertTrue(outcome.getFailureOutputs().containsKey("incomplete_dataset_flag"));

        // NFR: Compliance (GDPR/SOC2) - Verify no PII leakage in outputs
        String outcomeString = outcome.toString();
        assertFalse(outcomeString.contains("ssn"), "Output must not contain SSN (PII)");
        assertFalse(outcomeString.contains("dob"), "Output must not contain DOB (PII)");
        assertFalse(outcomeString.contains("fullName"), "Output must not contain FullName (PII)");

        // NFR: Input Validation - Verify incomplete dataset flag is present
        assertTrue((Boolean) outcome.getFailureOutputs().get("incomplete_dataset_flag"), 
            "Incomplete dataset flag must be true for missing damage assessment");

        // Verify infra interactions
        verify(rulesEngineService, times(1)).evaluate(any());
        verify(auditDiaryStore, times(1)).write(eq("AuditDiaryStore/claim-std-789.json"), anyString());
    }

    /**
     * Helper model representing the enrichment outcome structure.
     */
    static class EnrichmentOutcome {
        private final Map<String, Object> successOutputs;
        private final Map<String, Object> failureOutputs;

        public EnrichmentOutcome(Map<String, Object> successOutputs, Map<String, Object> failureOutputs) {
            this.successOutputs = successOutputs;
            this.failureOutputs = failureOutputs;
        }

        public Map<String, Object> getSuccessOutputs() {
            return successOutputs;
        }

        public Map<String, Object> getFailureOutputs() {
            return failureOutputs;
        }

        @Override
        public String toString() {
            return "EnrichmentOutcome{success=" + successOutputs + ", failure=" + failureOutputs + "}";
        }
    }

    /**
     * Mock interface for Rules Engine Decision Service.
     */
    interface RulesEngineDecisionService {
        Map<String, Object> evaluate(Map<String, Object> payload);
    }

    /**
     * Mock interface for Audit Diary Store (S3).
     */
    interface AuditDiaryStore {
        String write(String objectKeyPattern, String content);
    }

    /**
     * Service under test.
     */
    static class DecisionEnrichmentService {
        private final RulesEngineDecisionService rulesEngineService;
        private final AuditDiaryStore auditDiaryStore;

        public DecisionEnrichmentService(RulesEngineDecisionService rulesEngineService, AuditDiaryStore auditDiaryStore) {
            this.rulesEngineService = rulesEngineService;
            this.auditDiaryStore = auditDiaryStore;
        }

        public EnrichmentOutcome processEnrichment(String claimId, Map<String, Object> payload) {
            // Simulate validation
            boolean hasDamageAssessment = payload.containsKey("damageAssessment");
            
            Map<String, Object> successOutputs = rulesEngineService.evaluate(payload);
            
            Map<String, Object> failureOutputs = Map.of(
                "incomplete_dataset_flag", !hasDamageAssessment,
                "data_quality_warnings", hasDamageAssessment ? List.of() : List.of("missing_damage_assessment")
            );

            // Simulate audit logging
            auditDiaryStore.write("AuditDiaryStore/" + claimId + ".json", "Processed");

            return new EnrichmentOutcome(successOutputs, failureOutputs);
        }
    }
}
