package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolValidationDecisionMockTest {

    @Mock
    private FnolValidationService fnolValidationService;

    @Mock
    private ComplianceReportService complianceReportService;

    @Mock
    private RetentionStatusService retentionStatusService;

    @Mock
    private RemediationTaskService remediationTaskService;

    @Mock
    private AuditService auditService;

    private FnolDecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        decisionEngine = new FnolDecisionEngine(
                fnolValidationService,
                complianceReportService,
                retentionStatusService,
                remediationTaskService,
                auditService
        );
    }

    @Test
    void outputCriteriaSuccessOutputsComplianceReportObjectRetentionStatusEnumRemediationTasksListFailureOutputsAuditErrorCodeMissingEvidenceList() {
        // Arrange: Simulate multi-channel FNOL submission
        String submissionId = "FNOL-8821-CH";
        var submission = new FnolSubmission(submissionId, "MOBILE_APP", "POL-998877");

        // Mock success outputs
        var complianceReport = Map.<String, Object>of(
                "report_id", "CR-2023-10-27-001",
                "status", "VALIDATED",
                "generated_at", "2023-10-27T14:30:00Z"
        );
        var retentionStatus = RetentionStatusEnum.ACTIVE;
        var remediationTasks = List.of("verify_ownership", "confirm_damage_scope");

        // Mock failure/warning outputs
        var auditErrorCode = "AUDIT_ERR_MISSING_EVIDENCE";
        var missingEvidenceList = List.of("front_photo", "police_report");

        when(complianceReportService.generateReport(anyString())).thenReturn(complianceReport);
        when(retentionStatusService.getStatus(anyString())).thenReturn(retentionStatus);
        when(remediationTaskService.getTasks(anyString())).thenReturn(remediationTasks);
        when(auditService.getErrorCode(anyString())).thenReturn(auditErrorCode);
        when(auditService.getMissingEvidence(anyString())).thenReturn(missingEvidenceList);

        // Act: Execute validation decision
        var decisionResult = decisionEngine.evaluate(submission);

        // Assert: Verify success outputs structure and presence
        assertNotNull(decisionResult, "Decision result must not be null");
        assertTrue(decisionResult.containsKey("compliance_report"), "compliance_report must be present");
        assertInstanceOf(Map.class, decisionResult.get("compliance_report"), "compliance_report must be an object");

        assertTrue(decisionResult.containsKey("retention_status"), "retention_status must be present");
        assertInstanceOf(RetentionStatusEnum.class, decisionResult.get("retention_status"), "retention_status must be an enum");

        assertTrue(decisionResult.containsKey("remediation_tasks"), "remediation_tasks must be present");
        assertInstanceOf(List.class, decisionResult.get("remediation_tasks"), "remediation_tasks must be a list");
        assertFalse(((List<?>) decisionResult.get("remediation_tasks")).isEmpty(), "remediation_tasks list must not be empty");

        // Assert: Verify failure/warning outputs structure and presence
        assertTrue(decisionResult.containsKey("audit_error_code"), "audit_error_code must be present");
        assertInstanceOf(String.class, decisionResult.get("audit_error_code"), "audit_error_code must be a string");

        assertTrue(decisionResult.containsKey("missing_evidence_list"), "missing_evidence_list must be present");
        assertInstanceOf(List.class, decisionResult.get("missing_evidence_list"), "missing_evidence_list must be a list");
        assertFalse(((List<?>) decisionResult.get("missing_evidence_list")).isEmpty(), "missing_evidence_list must not be empty");
    }

    // --- Supporting Interfaces & Classes for Compilation ---

    enum RetentionStatusEnum {
        ACTIVE, ARCHIVED, ERASED
    }

    interface FnolValidationService {
        boolean validate(FnolSubmission submission);
    }

    interface ComplianceReportService {
        Map<String, Object> generateReport(String submissionId);
    }

    interface RetentionStatusService {
        RetentionStatusEnum getStatus(String submissionId);
    }

    interface RemediationTaskService {
        List<String> getTasks(String submissionId);
    }

    interface AuditService {
        String getErrorCode(String submissionId);
        List<String> getMissingEvidence(String submissionId);
    }

    record FnolSubmission(String submissionId, String channel, String policyId) {}

    class FnolDecisionEngine {
        private final FnolValidationService validationService;
        private final ComplianceReportService complianceReportService;
        private final RetentionStatusService retentionStatusService;
        private final RemediationTaskService remediationTaskService;
        private final AuditService auditService;

        FnolDecisionEngine(FnolValidationService validationService,
                           ComplianceReportService complianceReportService,
                           RetentionStatusService retentionStatusService,
                           RemediationTaskService remediationTaskService,
                           AuditService auditService) {
            this.validationService = validationService;
            this.complianceReportService = complianceReportService;
            this.retentionStatusService = retentionStatusService;
            this.remediationTaskService = remediationTaskService;
            this.auditService = auditService;
        }

        Map<String, Object> evaluate(FnolSubmission submission) {
            var result = new java.util.HashMap<String, Object>();
            // Simulate decision pipeline
            result.put("compliance_report", complianceReportService.generateReport(submission.submissionId()));
            result.put("retention_status", retentionStatusService.getStatus(submission.submissionId()));
            result.put("remediation_tasks", remediationTaskService.getTasks(submission.submissionId()));
            result.put("audit_error_code", auditService.getErrorCode(submission.submissionId()));
            result.put("missing_evidence_list", auditService.getMissingEvidence(submission.submissionId()));
            return result;
        }
    }
}
