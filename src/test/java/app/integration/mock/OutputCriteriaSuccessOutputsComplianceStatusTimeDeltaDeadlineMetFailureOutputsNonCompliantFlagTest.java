package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutputCriteriaSuccessOutputsComplianceStatusTimeDeltaDeadlineMetFailureOutputsNonCompliantFlagTest {

    @Mock
    private ComplianceAuditService mockAuditService;
    @Mock
    private PolicyClaimsDbClient mockDbClient;
    @Mock
    private EventPublisher mockEventPublisher;

    private ComplianceOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ComplianceOrchestrator(mockAuditService, mockDbClient, mockEventPublisher);
    }

    @Test
    @DisplayName("output_criteria_success_outputs_compliance_status_time_delta_deadline_met_failure_outputs_non_compliant_flag_remediation_task_created_status_updates_audit_status_compliant_noncompliant_remediation_emitted_events_compliance_audit_completed_compliance_remediation_triggered_user_visible_outputs_compliance_dashboard_edge_cases_system_downtime_during_deadline_window_manual_acknowledgment_override_negative_scenarios_missing_acknowledgment_record_incorrect_deadline_configuration_explainability_expectations_deadline_applied_time_calculation_breakdown_holiday_business_hour_adjustments_audit_evidence_submission_acknowledgment_timestamps_configuration_version_used_calculation_log_sample_test_scenarios_scenario_acknowledgment_within_deadline_given_submission_at_10_00_am_ack_at_12_00_pm_next_day_when_audit_runs_then_status_compliant_log_entry_created")
    void outputCriteriaSuccessOutputsComplianceStatusTimeDeltaDeadlineMetFailureOutputsNonCompliantFlag() {
        // Given
        LocalDateTime submissionTime = LocalDateTime.of(2023, 10, 25, 10, 0);
        LocalDateTime acknowledgmentTime = LocalDateTime.of(2023, 10, 26, 12, 0);
        String claimId = "CLM-123";
        int deadlineHours = 48; // Configured deadline allowing compliance for this scenario

        // When
        Map<String, Object> result = orchestrator.processComplianceCheck(claimId, submissionTime, acknowledgmentTime, deadlineHours);

        // Then: Verify success outputs
        assertEquals("COMPLIANT", result.get("compliance_status"));
        assertEquals(26, result.get("time_delta")); // hours elapsed
        assertTrue((boolean) result.get("deadline_met"));
        assertFalse((boolean) result.get("non_compliant_flag"));
        assertFalse((boolean) result.get("remediation_task_created"));

        // Verify status updates
        ArgumentCaptor<Map<String, Object>> dbUpdateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockDbClient).updateClaimStatus(eq(claimId), dbUpdateCaptor.capture());
        assertEquals("Audit status -> Compliant", dbUpdateCaptor.getValue().get("status_update"));

        // Verify emitted events
        verify(mockEventPublisher).publish("Compliance.Audit.Completed", claimId);

        // Verify S3 audit evidence write
        ArgumentCaptor<Map<String, Object>> auditLogCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockAuditService).writeAuditLog(eq("ComplianceAuditService-bucket"), anyString(), auditLogCaptor.capture());
        Map<String, Object> logPayload = auditLogCaptor.getValue();
        assertNotNull(logPayload.get("calculation_log"));
        assertEquals("Configuration version used: v1.0", logPayload.get("configuration_version"));
        assertEquals("Deadline applied: 48h", logPayload.get("deadline_applied"));
    }
}
