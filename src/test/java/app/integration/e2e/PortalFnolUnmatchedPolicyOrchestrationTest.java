package app.integration.e2e;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import app.services.ClaimOrchestrationService;
import app.models.Claim;
import app.models.Task;
import app.models.AuditEvent;
import java.util.HashMap;
import java.util.Map;

public class PortalFnolUnmatchedPolicyOrchestrationTest {
    private ClaimOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // E2E: Wire real application services. Compile stubs generated under src/main/java/app/
        // satisfy the interface contract. No live AWS or production HTTP APIs are invoked.
        orchestrationService = new ClaimOrchestrationService();
    }

    @Test
    void orchestratePortalFnolUnmatchedPolicyShell() {
        // Build inputs from test-case Inputs and Constants JSON sidecar
        Map<String, Object> fnolPayload = new HashMap<>();
        fnolPayload.put("channel", "insured_portal");
        fnolPayload.put("policy_number", "POL-DP3-FL-1122");
        fnolPayload.put("date_of_loss", "2024-06-01T14:30:00Z");
        fnolPayload.put("cause_of_loss", "water_leak");
        fnolPayload.put("risk_address", "456 Oak Ave");
        fnolPayload.put("city", "Tampa");
        fnolPayload.put("state", "FL");
        fnolPayload.put("zip", "33602");
        fnolPayload.put("reporter_type", "tenant");
        fnolPayload.put("product_form", "HO3");
        fnolPayload.put("loss_description", "Kitchen pipe burst causing interior water damage");

        // Invoke real orchestration service end-to-end
        Claim claimResult = orchestrationService.intakeAndOrchestrate(fnolPayload);

        // Expected Results: System normalizes payload to unified claim data model
        assertNotNull(claimResult);

        // Expected Results: policy lookup returns no active match, creates intake shell with state Unmatched Policy
        assertEquals("Unmatched Policy", claimResult.getState());

        // Expected Results: claim_number remains null
        assertNull(claimResult.getClaimNumber());

        // Expected Results: creates task Resolve Policy Match
        Task resolveTask = claimResult.getTasks().stream()
                .filter(t -> "Resolve Policy Match".equals(t.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected Resolve Policy Match task not found"));
        assertNotNull(resolveTask);

        // Expected Results: does not queue acknowledgment
        assertFalse(claimResult.isAcknowledgmentQueued());

        // Expected Results: writes audit event with tenant_id, actor=portal, timestamp, action=fnol_intake_created, reason=unmatched_policy
        AuditEvent auditEvent = claimResult.getAuditEvent();
        assertNotNull(auditEvent);
        assertNotNull(auditEvent.getTenantId());
        assertEquals("portal", auditEvent.getActor());
        assertNotNull(auditEvent.getTimestamp());
        assertEquals("fnol_intake_created", auditEvent.getAction());
        assertEquals("unmatched_policy", auditEvent.getReason());
    }
}
