package app.integration.e2e;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Imports assumed from app.services.* and app.models.* per project conventions
import app.services.OrchestrationDecisionService;
import app.models.ClaimSubmission;
import app.models.ClaimState;
import app.models.Task;
import app.models.CommunicationRouting;
import app.models.StatutoryDiary;

public class PortalCoverageReviewAttorneyE2eTest {

    private OrchestrationDecisionService orchestrationService;

    @BeforeEach
    void setUp() {
        // In a real E2E environment, this would be resolved from the application context
        orchestrationService = new OrchestrationDecisionService();
    }

    @Test
    void portal_intake_coverage_review_attorney_claim() {
        // Construct payload from test-case Inputs and Constants JSON sidecar
        ClaimSubmission submission = ClaimSubmission.builder()
                .channel("portal")
                .product("HO3")
                .policyNumber("POL-FL-67890")
                .dateOfLoss("2024-05-15")
                .causeOfLoss("water")
                .severity("medium")
                .policyStatus("expired")
                .attorneyFlag(true)
                .reporterType("attorney")
                .paFlag(false)
                .aobFlag(false)
                .build();

        // Invoke orchestration decision service
        ClaimState result = orchestrationService.decide(submission);

        // Verify Expected Results: State transitions to Coverage Triage
        assertEquals("Coverage Triage", result.getState(), "State should transition to Coverage Triage");

        // Verify Expected Results: Claim type assigned as Coverage review claim
        assertEquals("Coverage review claim", result.getClaimType(), "Claim type should be Coverage review claim");

        // Verify Expected Results: Tasks created
        assertTrue(result.getTasks().stream().anyMatch(t -> "Attorney Representation Review".equals(t.getName())),
                "Task 'Attorney Representation Review' should be created");
        assertTrue(result.getTasks().stream().anyMatch(t -> "Review Coverage".equals(t.getName())),
                "Task 'Review Coverage' should be created");
        assertTrue(result.getTasks().stream().anyMatch(t -> "Request Missing Information".equals(t.getName())),
                "Task 'Request Missing Information' should be created");

        // Verify Expected Results: Communication workflow restricted to represented-claim routing
        assertEquals("represented-claim", result.getCommunicationRouting().getRoutingType(),
                "Communication workflow should be restricted to represented-claim routing");

        // Verify Expected Results: Statutory diaries initialized
        assertTrue(result.getStatutoryDiaries().stream().anyMatch(d -> "representation document".equals(d.getName())),
                "Statutory diary for representation document should be initialized");
        assertTrue(result.getStatutoryDiaries().stream().anyMatch(d -> "investigation start".equals(d.getName())),
                "Statutory diary for investigation start should be initialized");
    }
}
