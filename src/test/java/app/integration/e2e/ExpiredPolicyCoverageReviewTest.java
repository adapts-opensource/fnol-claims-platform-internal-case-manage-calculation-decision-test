package app.integration.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import app.services.FnolIntakeService;
import app.services.CoverageTriageService;
import app.services.AuditLogService;
import app.models.AuditEvent;

/**
 * E2E verification for Insured Engagement & Tracking:transformation:validation.
 * Aligns with NFRs: thread-safe service wiring, structured logging via AuditLogService,
 * input validation enforced by FnolIntakeService, GDPR/SOC2 data handling in payloads.
 */
public class InsuredEngagementTransformationValidationE2eTest {

    private static final Map<String, Object> TEST_CONSTANTS = Map.of(
        "policy_number", "POL-12345",
        "risk_address", "456 Oak Ave",
        "date_of_loss", "2024-06-15",
        "policy_expiration_date", "2024-05-01",
        "cause_of_loss", "Fire",
        "product_form", "HO3",
        "reporter_name", "Jane Smith",
        "reporter_type", "Tenant",
        "claim_channel", "Agent-assisted"
    );

    private FnolIntakeService fnolIntakeService;
    private CoverageTriageService coverageTriageService;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        // In production E2E, services are injected via DI container/test containers.
        // For compilation stubs, direct instantiation is used to exercise real service paths.
        fnolIntakeService = new FnolIntakeService();
        coverageTriageService = new CoverageTriageService();
        auditLogService = new AuditLogService();
    }

    @Test
    void validate_fnol_intake_expired_policy() {
        // Build inputs from Constants JSON sidecar and test-case Inputs
        Map<String, Object> payload = new HashMap<>(TEST_CONSTANTS);
        payload.put("id", UUID.randomUUID().toString());

        // Execute: Submit FNOL intake to real service
        Map<String, Object> intakeResult = fnolIntakeService.submitIntake(
            (String) payload.get("id"), payload
        );

        // Expected Results: Claim state transitions to Coverage Triage
        assertEquals("COVERAGE_TRIAGE", intakeResult.get("claim_state"),
            "Claim state should transition to Coverage Triage");

        // Expected Results: Policy match fails active period validation
        Boolean policyActive = (Boolean) intakeResult.get("policy_active");
        assertFalse(policyActive, "Policy match should fail active period validation");

        // Expected Results: Task Review Coverage is generated
        @SuppressWarnings("unchecked")
        List<String> tasks = (List<String>) intakeResult.get("generated_tasks");
        assertTrue(tasks.contains("Review Coverage"),
            "Task 'Review Coverage' should be generated");

        // Expected Results: Task Request Missing Information is created
        assertTrue(tasks.contains("Request Missing Information"),
            "Task 'Request Missing Information' should be created");

        // Expected Results: Acknowledgment is sent noting coverage review pending
        String acknowledgment = (String) intakeResult.get("acknowledgment_message");
        assertNotNull(acknowledgment, "Acknowledgment should be sent");
        assertTrue(acknowledgment.toLowerCase().contains("coverage review pending"),
            "Acknowledgment should note coverage review pending");

        // Expected Results: Audit log records the policy period mismatch and transformation to coverage triage path
        List<AuditEvent> auditLogs = auditLogService.getRecentEvents((String) payload.get("id"));
        assertFalse(auditLogs.isEmpty(), "Audit log should record events");
        AuditEvent lastEvent = auditLogs.get(auditLogs.size() - 1);
        assertTrue(lastEvent.getMessage().contains("policy period mismatch"),
            "Audit log should record policy period mismatch");
        assertTrue(lastEvent.getMessage().contains("transformation to coverage triage path"),
            "Audit log should record transformation to coverage triage path");
    }
}
