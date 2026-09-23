package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock test for Claim Initiation & Routing:decision:calculation feature.
 * Verifies output criteria for the success path including compliance reporting,
 * integrity verification, status updates, and event emission, while ensuring
 * failure outputs are not generated.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationMockTest {

    @Mock
    private DecisionCalculationService decisionCalculationService;

    @Mock
    private ReportGenerator complianceReportGenerator;

    @Mock
    private IntegrityVerifier integrityVerifier;

    @Mock
    private StatusUpdateService statusUpdateService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private GapReportGenerator gapReportGenerator;

    @Mock
    private AlertService alertService;

    private static final String CLAIM_ID = "CLAIM-001";
    private static final String STATUS_AUDIT_COMPLETED = "Audit completed";
    private static final String EVENT_AUDIT_REPORT_GENERATED = "audit.report.generated";
    private static final String SUCCESS_OUTPUT_COMPLIANCE_REPORT = "Compliance report generated";
    private static final String SUCCESS_OUTPUT_INTEGRITY_VERIFICATION = "Integrity verification passed";
    private static final String FAILURE_OUTPUT_GAP_REPORT = "Gap report";
    private static final String FAILURE_OUTPUT_ALERT_STEWARD = "Alert to data steward";

    @Test
    void output_criteria_success_outputs_compliance_report_generated_integrity_verification_passed_failure_outputs_gap_report_alert_to_data_steward_status_updates_audit_completed_emitted_events_audit_report_generated() {
        // Given
        Map<String, Object> payload = Map.of(
                "claimId", CLAIM_ID,
                "type", "AUTO",
                "routingContext", "INITIATION"
        );

        // Mock success behavior
        when(integrityVerifier.verify(payload)).thenReturn(true);
        when(complianceReportGenerator.generateReport(payload)).thenReturn("REPORT-UUID-123");

        // When
        DecisionResult result = decisionCalculationService.calculateAndRoute(payload);

        // Then: Verify Success Outputs
        // 1. Compliance report generated
        verify(complianceReportGenerator).generateReport(payload);
        
        // 2. Integrity verification passed
        verify(integrityVerifier).verify(payload);
        assertTrue(result.isIntegrityPassed(), "Integrity verification should have passed");

        // Then: Verify Status Updates
        // Status updated to 'Audit completed'
        verify(statusUpdateService).updateStatus(eq(CLAIM_ID), eq(STATUS_AUDIT_COMPLETED));

        // Then: Verify Emitted Events
        // Event 'audit.report.generated' emitted
        verify(eventPublisher).publish(eq(EVENT_AUDIT_REPORT_GENERATED), any());

        // Then: Verify Failure Outputs are NOT generated
        // Gap report should not be generated
        verifyZeroInteractions(gapReportGenerator);
        
        // Alert to data steward should not be sent
        verifyZeroInteractions(alertService);

        // Optional: Validate result structure if applicable
        assertNotNull(result);
        assertEquals(SUCCESS_OUTPUT_COMPLIANCE_REPORT, result.getSuccessOutputs().get(0), "First success output mismatch");
        assertEquals(SUCCESS_OUTPUT_INTEGRITY_VERIFICATION, result.getSuccessOutputs().get(1), "Second success output mismatch");
    }
}
