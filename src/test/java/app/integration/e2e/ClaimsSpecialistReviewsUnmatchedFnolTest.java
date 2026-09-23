package app.integration.e2e;

import app.models.ClaimInitiation;
import app.services.ClaimRoutingService;
import app.services.TaskWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ClaimsSpecialistReviewsUnmatchedFnolTest {

    private ClaimRoutingService claimRoutingService;
    private TaskWorkflowService taskWorkflowService;

    @BeforeEach
    void setUp() {
        // Real services initialized; compiled stubs provided under src/main/java/app/
        claimRoutingService = new ClaimRoutingService();
        taskWorkflowService = new TaskWorkflowService();
    }

    @Test
    void us_fnol_02_claims_specialist_reviews_unmatched_fnol() {
        // Build inputs from test-case Inputs / Expected Results and the Constants JSON sidecar
        String claimId = "CLM-UNMATCHED-001";
        Map<String, Object> payload = Map.of(
                "claimId", claimId,
                "type", "FNOL",
                "matchStatus", "UNMATCHED",
                "initiatorRole", "CLAIMS_SPECIALIST",
                "requiresPolicyMatch", true
        );

        ClaimInitiation claimInitiation = new ClaimInitiation();
        claimInitiation.setId(claimId);
        claimInitiation.setPayload(payload);

        // Execute: System creates Resolve Policy Match task or Unmatched FNOL shell
        String routingOutcome = claimRoutingService.validateRoutingDecision(claimInitiation);

        // Expected Results: Override workflow enforces reason codes and role checks
        assertNotNull(routingOutcome, "Routing decision must be produced for unmatched FNOL");
        assertEquals("RESOLVE_POLICY_MATCH_TASK", routingOutcome, "System should create Resolve Policy Match task");

        Map<String, Object> workflowContext = taskWorkflowService.getWorkflowContext(claimId);
        assertTrue((Boolean) workflowContext.get("overrideActive"), "Override workflow must be active for specialist review");
        assertNotNull(workflowContext.get("reasonCodeValidation"), "Reason code enforcement must be active");
        assertEquals("CLAIMS_SPECIALIST", workflowContext.get("authorizedRole"), "Role check must enforce CLAIMS_SPECIALIST");
    }
}
