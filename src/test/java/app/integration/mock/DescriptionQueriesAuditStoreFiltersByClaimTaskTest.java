package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DescriptionQueriesAuditStoreFiltersByClaimTaskTest {

    @Mock
    private AuditStoreClient auditStoreClient;
    @Mock
    private CompletenessValidator completenessValidator;
    @Mock
    private ExplainabilityReporter explainabilityReporter;
    @Mock
    private AnomalyFlagger anomalyFlagger;

    @InjectMocks
    private ClaimDataStandardizationDecisionService decisionService;

    private static final String CLAIM_ID = "claim-123";
    private static final String TASK_ID = "task-456";
    private static final String CONFIG_ID = "config-789";

    @BeforeEach
    void setUp() {
        // Mock initialization handled automatically by MockitoExtension
    }

    @Test
    void description_queries_audit_store_filters_by_claim_task_config_validates_completeness_generates_explainability_report_and_flags_anomalies() {
        // Arrange
        AuditQueryRequest request = new AuditQueryRequest(CLAIM_ID, TASK_ID, CONFIG_ID);
        List<AuditRecord> mockRecords = List.of(
                new AuditRecord(UUID.randomUUID().toString(), Map.of("claimType", "auto", "severity", "high")),
                new AuditRecord(UUID.randomUUID().toString(), Map.of("claimType", "property", "severity", "low"))
        );
        when(auditStoreClient.queryAndFilter(eq(request))).thenReturn(mockRecords);

        Map<String, Object> validatedPayload = Map.of("isComplete", true, "completenessScore", 1.0);
        when(completenessValidator.validate(any(Map.class))).thenReturn(validatedPayload);

        ExplainabilityReport report = new ExplainabilityReport("exp-report-001", "Standardization rules applied successfully.");
        when(explainabilityReporter.generate(any(Map.class))).thenReturn(report);

        List<Anomaly> anomalies = List.of(new Anomaly("claimType", "unexpected_mapping"));
        when(anomalyFlagger.flag(any(Map.class))).thenReturn(anomalies);

        // Act
        DecisionOutcome outcome = decisionService.processDecision(request);

        // Assert
        assertNotNull(outcome, "Decision outcome should not be null");
        assertTrue(outcome.isCompletenessValid(), "Completeness validation should pass");
        assertEquals("exp-report-001", outcome.getExplainabilityReportId(), "Explainability report ID should match");
        assertEquals(1, outcome.getAnomalies().size(), "Should flag exactly one anomaly");
        assertEquals("unexpected_mapping", outcome.getAnomalies().get(0).getDetail(), "Anomaly detail should match");

        // Verify external I/O and component interactions
        verify(auditStoreClient, times(1)).queryAndFilter(request);
        verify(completenessValidator, times(1)).validate(any(Map.class));
        verify(explainabilityReporter, times(1)).generate(any(Map.class));
        verify(anomalyFlagger, times(1)).flag(any(Map.class));
    }

    // Supporting DTOs and Service Stub for compilation
    private static class AuditQueryRequest {
        private final String claimId;
        private final String taskId;
        private final String configId;
        public AuditQueryRequest(String claimId, String taskId, String configId) {
            this.claimId = claimId;
            this.taskId = taskId;
            this.configId = configId;
        }
        public String getClaimId() { return claimId; }
        public String getTaskId() { return taskId; }
        public String getConfigId() { return configId; }
    }

    private static class AuditRecord {
        private final String id;
        private final Map<String, Object> payload;
        public AuditRecord(String id, Map<String, Object> payload) {
            this.id = id;
            this.payload = payload;
        }
        public String getId() { return id; }
        public Map<String, Object> getPayload() { return payload; }
    }

    private static class ExplainabilityReport {
        private final String reportId;
        private final String explanation;
        public ExplainabilityReport(String reportId, String explanation) {
            this.reportId = reportId;
            this.explanation = explanation;
        }
        public String getReportId() { return reportId; }
        public String getExplanation() { return explanation; }
    }

    private static class Anomaly {
        private final String field;
        private final String detail;
        public Anomaly(String field, String detail) {
            this.field = field;
            this.detail = detail;
        }
        public String getField() { return field; }
        public String getDetail() { return detail; }
    }

    private static class DecisionOutcome {
        private final boolean completenessValid;
        private final String explainabilityReportId;
        private final List<Anomaly> anomalies;
        public DecisionOutcome(boolean completenessValid, String explainabilityReportId, List<Anomaly> anomalies) {
            this.completenessValid = completenessValid;
            this.explainabilityReportId = explainabilityReportId;
            this.anomalies = anomalies;
        }
        public boolean isCompletenessValid() { return completenessValid; }
        public String getExplainabilityReportId() { return explainabilityReportId; }
        public List<Anomaly> getAnomalies() { return anomalies; }
    }

    private interface AuditStoreClient {
        List<AuditRecord> queryAndFilter(AuditQueryRequest request);
    }
    private interface CompletenessValidator {
        Map<String, Object> validate(Map<String, Object> payload);
    }
    private interface ExplainabilityReporter {
        ExplainabilityReport generate(Map<String, Object> payload);
    }
    private interface AnomalyFlagger {
        List<Anomaly> flag(Map<String, Object> payload);
    }

    private static class ClaimDataStandardizationDecisionService {
        private AuditStoreClient auditStoreClient;
        private CompletenessValidator completenessValidator;
        private ExplainabilityReporter explainabilityReporter;
        private AnomalyFlagger anomalyFlagger;

        public void setAuditStoreClient(AuditStoreClient auditStoreClient) {
            this.auditStoreClient = auditStoreClient;
        }
        public void setCompletenessValidator(CompletenessValidator completenessValidator) {
            this.completenessValidator = completenessValidator;
        }
        public void setExplainabilityReporter(ExplainabilityReporter explainabilityReporter) {
            this.explainabilityReporter = explainabilityReporter;
        }
        public void setAnomalyFlagger(AnomalyFlagger anomalyFlagger) {
            this.anomalyFlagger = anomalyFlagger;
        }

        public DecisionOutcome processDecision(AuditQueryRequest request) {
            List<AuditRecord> records = auditStoreClient.queryAndFilter(request);
            Map<String, Object> combinedPayload = new HashMap<>();
            for (AuditRecord r : records) {
                combinedPayload.putAll(r.getPayload());
            }

            Map<String, Object> validatedPayload = completenessValidator.validate(combinedPayload);
            boolean isComplete = Boolean.TRUE.equals(validatedPayload.get("isComplete"));

            ExplainabilityReport report = explainabilityReporter.generate(combinedPayload);
            List<Anomaly> anomalies = anomalyFlagger.flag(combinedPayload);

            return new DecisionOutcome(isComplete, report.getReportId(), anomalies);
        }
    }
}
