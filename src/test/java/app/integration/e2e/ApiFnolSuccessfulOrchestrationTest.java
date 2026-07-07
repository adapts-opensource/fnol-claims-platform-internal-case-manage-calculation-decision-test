package app.integration.e2e;

import app.models.AuditEvent;
import app.models.Claim;
import app.models.Task;
import app.services.AuditLogService;
import app.services.EmailNotificationService;
import app.services.FnolOrchestrationService;
import app.services.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class ApiFnolSuccessfulOrchestrationTest {

    private FnolOrchestrationService orchestrationService;
    private TaskService taskService;
    private EmailNotificationService emailNotificationService;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        // Wire real application services (stubs compiled under src/main/java/app/)
        orchestrationService = new FnolOrchestrationService();
        taskService = new TaskService();
        emailNotificationService = new EmailNotificationService();
        auditLogService = new AuditLogService();
    }

    @Test
    void orchestrate_api_fnol_successful_creation() {
        // Build inputs from test-case Inputs / Expected Results and Constants JSON sidecar
        String channel = "api";
        String policyNumber = "POL-HO3-FL-8842";
        String dateOfLoss = "2024-05-15T10:00:00Z";
        String causeOfLoss = "wind";
        String riskAddress = "123 Main St";
        String city = "Miami";
        String state = "FL";
        String zip = "33101";
        String reporterType = "named_insured";
        String productForm = "HO3";
        String lossDescription = "Roof shingle damage from wind storm";
        String expectedClaimNumber = "CLM-FL01-2024-00001235";
        String expectedState = "Claim Opened";
        List<String> expectedTaskNames = List.of("Review FNOL", "Acknowledge Claim");
        String expectedEmailProvider = "ses";
        String tenantId = "tenant_001";
        String expectedActor = "api";
        String expectedAction = "fnol_submitted";

        // Construct unified payload at service boundary
        Map<String, String> submissionPayload = Map.of(
            "channel", channel,
            "policy_number", policyNumber,
            "date_of_loss", dateOfLoss,
            "cause_of_loss", causeOfLoss,
            "risk_address", riskAddress,
            "city", city,
            "state", state,
            "zip", zip,
            "reporter_type", reporterType,
            "product_form", productForm,
            "loss_description", lossDescription,
            "tenant_id", tenantId
        );

        // Execute end-to-end orchestration
        Claim normalizedClaim = orchestrationService.submitAndOrchestrate(submissionPayload);

        // Assert: System normalizes payload to unified claim data model
        assertNotNull(normalizedClaim, "Normalized claim must be returned");
        assertEquals(expectedClaimNumber, normalizedClaim.getClaimNumber(), "Generated claim number must match expected");
        assertEquals(expectedState, normalizedClaim.getState(), "Claim state must transition to Claim Opened");
        assertEquals(tenantId, normalizedClaim.getTenantId(), "Tenant ID must be preserved in unified model");

        // Assert: Creates standard tasks
        List<Task> createdTasks = taskService.findByClaimId(normalizedClaim.getClaimId());
        assertNotNull(createdTasks, "Tasks must be created");
        assertEquals(expectedTaskNames.size(), createdTasks.size(), "Must create expected number of tasks");
        for (int i = 0; i < expectedTaskNames.size(); i++) {
            assertEquals(expectedTaskNames.get(i), createdTasks.get(i).getName(), "Task name must match expected");
        }

        // Assert: Queues acknowledgment email via SES
        List<String> queuedProviders = emailNotificationService.getQueuedProviders();
        assertFalse(queuedProviders.isEmpty(), "Acknowledgment email must be queued");
        assertEquals(expectedEmailProvider, queuedProviders.get(0), "Email provider must be SES");

        // Assert: Writes audit event with required fields
        AuditEvent auditEvent = auditLogService.findLatestByClaimId(normalizedClaim.getClaimId());
        assertNotNull(auditEvent, "Audit event must be written");
        assertEquals(normalizedClaim.getClaimNumber(), auditEvent.getClaimNumber(), "Audit must reference claim number");
        assertEquals(expectedActor, auditEvent.getActor(), "Audit actor must be api");
        assertEquals(expectedAction, auditEvent.getAction(), "Audit action must be fnol_submitted");
        assertNotNull(auditEvent.getTimestamp(), "Audit must have timestamp");
        assertEquals(tenantId, auditEvent.getTenantId(), "Audit must include tenant_id");
    }
}
