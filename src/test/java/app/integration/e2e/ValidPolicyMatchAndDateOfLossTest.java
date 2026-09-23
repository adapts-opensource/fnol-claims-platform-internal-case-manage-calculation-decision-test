package app.integration.e2e;

import app.models.InsuredEngagementTrackingTransformationVal;
import app.services.AuditService;
import app.services.ClaimService;
import app.services.CommunicationService;
import app.services.FnolIntakeService;
import app.services.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "APP_BASE_URL", matches = ".*")
public class InsuredEngagementTrackingValidationE2ETest {

    private FnolIntakeService fnolIntakeService;
    private ClaimService claimService;
    private TaskService taskService;
    private CommunicationService communicationService;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        // Wire real application services from app.services.*
        // Stubs under src/main/java/app/ are compiled by the build environment.
        fnolIntakeService = new FnolIntakeService();
        claimService = new ClaimService();
        taskService = new TaskService();
        communicationService = new CommunicationService();
        auditService = new AuditService();
    }

    @Test
    void validate_fnol_intake_valid_policy_match() {
        // Build inputs from test-case Inputs / Expected Results
        Map<String, Object> payload = new HashMap<>();
        payload.put("policy_number", "POL-98765");
        payload.put("risk_address", "123 Main St");
        payload.put("date_of_loss", "2024-05-10");
        payload.put("cause_of_loss", "Wind");
        payload.put("product_form", "HO3");
        payload.put("reporter_name", "John Doe");
        payload.put("reporter_type", "Named Insured");
        payload.put("claim_channel", "Insured Portal");

        // Construct entity per data model: insured_engagement___tracking_transformation_val
        InsuredEngagementTrackingTransformationVal entity = new InsuredEngagementTrackingTransformationVal();
        entity.setId(UUID.randomUUID().toString());
        entity.setPayload(payload);

        // Invoke real services end-to-end
        InsuredEngagementTrackingTransformationVal result = fnolIntakeService.validateAndTransform(entity);

        // Assert Expected Results
        assertNotNull(result, "Transformation result should not be null");
        assertEquals("Claim Opened", result.getPayload().get("claim_state"), "Claim state should transition to Claim Opened");
        assertNotNull(result.getPayload().get("claim_number"), "Claim number should be generated");
        assertEquals(true, result.getPayload().get("policy_match_active"), "Policy match should be confirmed as active");
        assertNotNull(result.getPayload().get("initial_reserve"), "Initial reserve should be set");
        assertEquals("Property", result.getPayload().get("claim_type"), "Should assign standard property claim type");

        // Verify tasks created
        String claimId = (String) result.getPayload().get("claim_number");
        List<String> createdTasks = taskService.listByClaimId(claimId);
        assertTrue(createdTasks.contains("Review FNOL"), "Task Review FNOL should be created");
        assertTrue(createdTasks.contains("Assign Adjuster"), "Task Assign Adjuster should be created");

        // Verify communication queued for outbound delivery
        assertTrue(communicationService.isQueuedForDelivery(claimId), "Acknowledgment communication should be queued");

        // Verify audit log records successful intake transformation
        assertTrue(auditService.hasRecord(claimId, "INTAKE_TRANSFORM_SUCCESS"), "Audit log should record successful intake transformation");
    }
}
