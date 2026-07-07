package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuditStoreQueryComplianceTest {

    @Mock
    private AuditStoreClient auditStoreClient;

    private AuditComplianceService auditComplianceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        auditComplianceService = new AuditComplianceService(auditStoreClient);
    }

    @Test
    void description_queries_audit_store_by_correlation_id_or_claim_ref_retrieves_decision_context_rule_traces_and_input_output_snapshots_validates_retention_period_and_completeness_returns_compliance_report() {
        String correlationId = "corr-001";
        String claimRef = "claim-002";
        Instant now = Instant.now();
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);

        DecisionContext context = new DecisionContext(thirtyDaysAgo, "fnol-policy-validation", "approved");
        List<RuleTrace> traces = List.of(new RuleTrace("trace-1", "passed", thirtyDaysAgo));
        Map<String, Object> inputSnapshot = Map.of("policy_id", "pol-789", "channel", "web");
        Map<String, Object> outputSnapshot = Map.of("decision", "approve", "score", 90);

        AuditRecord record = new AuditRecord(correlationId, claimRef, "tenant-ins-01", context, traces, inputSnapshot, outputSnapshot);
        when(auditStoreClient.queryByCorrelationIdOrClaimRef(correlationId, claimRef)).thenReturn(List.of(record));

        ComplianceReport report = auditComplianceService.queryAndValidate(correlationId, claimRef);

        assertNotNull(report, "Compliance report must not be null");
        assertEquals(ComplianceStatus.COMPLIANT, report.status(), "Report should be compliant under valid retention and completeness");
        assertTrue(report.retentionPeriodValid(), "Retention period must fall within SOC2/GDPR limits");
        assertTrue(report.isComplete(), "All decision context, traces, and snapshots must be present");
        assertEquals(1, report.records().size(), "Should retrieve exactly one matching audit record");
        verify(auditStoreClient, times(1)).queryByCorrelationIdOrClaimRef(correlationId, claimRef);
    }

    static record DecisionContext(Instant timestamp, String ruleId, String outcome) {}
    static record RuleTrace(String traceId, String status, Instant timestamp) {}
    static record AuditRecord(String correlationId, String claimRef, String tenantId, DecisionContext context, List<RuleTrace> traces, Map<String, Object> inputSnapshot, Map<String, Object> outputSnapshot) {}
    static record ComplianceReport(ComplianceStatus status, boolean retentionPeriodValid, boolean complete, List<AuditRecord> records) {}

    interface AuditStoreClient {
        List<AuditRecord> queryByCorrelationIdOrClaimRef(String correlationId, String claimRef);
    }

    static class AuditComplianceService {
        private final AuditStoreClient auditStoreClient;

        AuditComplianceService(AuditStoreClient auditStoreClient) {
            this.auditStoreClient = auditStoreClient;
        }

        ComplianceReport queryAndValidate(String correlationId, String claimRef) {
            List<AuditRecord> records = auditStoreClient.queryByCorrelationIdOrClaimRef(correlationId, claimRef);
            boolean complete = !records.isEmpty() && records.stream()
                    .allMatch(r -> r.context() != null && r.traces() != null && r.inputSnapshot() != null && r.outputSnapshot() != null);
            boolean retentionValid = records.stream()
                    .allMatch(r -> {
                        Instant ts = r.context().timestamp();
                        return ts != null && ChronoUnit.DAYS.between(ts, Instant.now()) <= 365;
                    });
            ComplianceStatus status = (complete && retentionValid) ? ComplianceStatus.COMPLIANT : ComplianceStatus.NON_COMPLIANT;
            return new ComplianceReport(status, retentionValid, complete, records);
        }
    }
}
