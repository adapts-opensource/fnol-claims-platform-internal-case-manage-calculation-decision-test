package app.integration.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import app.models.ClaimOutcome;
import app.models.FnolSubmissionRequest;
import app.models.TaskResult;
import app.services.ClaimStateService;
import app.services.DiaryService;
import app.services.OrchestrationDecisionService;
import app.services.TaskManagementService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ApiFastTrackHO3Test {

    private OrchestrationDecisionService orchestrationService;
    private ClaimStateService claimStateService;
    private TaskManagementService taskManagementService;
    private DiaryService diaryService;

    @BeforeEach
    void setUp() {
        // In a real E2E environment, these services are wired via the application context.
        // Compile stubs under src/main/java/app/ provide the required constructors.
        orchestrationService = new OrchestrationDecisionService();
        claimStateService = new ClaimStateService();
        taskManagementService = new TaskManagementService();
        diaryService = new DiaryService();
    }

    @Test
    void api_intake_fast_track_ho3_claim() {
        // Build inputs from test-case Inputs and Constants JSON sidecar
        Map<String, Object> fixture = Map.of(
            "channel", "api",
            "product", "HO3",
            "policy_number", "POL-FL-12345",
            "date_of_loss", "2024-05-15",
            "cause_of_loss", "wind",
            "severity", "low",
            "documentation", "sufficient",
            "attorney_flag", false,
            "pa_flag", false,
            "aob_flag", false
        );

        FnolSubmissionRequest request = new FnolSubmissionRequest();
        request.setChannel((String) fixture.get("channel"));
        request.setProduct((String) fixture.get("product"));
        request.setPolicyNumber((String) fixture.get("policy_number"));
        request.setDateOfLoss(LocalDate.parse((String) fixture.get("date_of_loss")));
        request.setCauseOfLoss((String) fixture.get("cause_of_loss"));
        request.setSeverity((String) fixture.get("severity"));
        request.setDocumentation((String) fixture.get("documentation"));
        request.setAttorneyFlag((Boolean) fixture.get("attorney_flag"));
        request.setPaFlag((Boolean) fixture.get("pa_flag"));
        request.setAobFlag((Boolean) fixture.get("aob_flag"));

        // Execute E2E orchestration via real app services
        ClaimOutcome outcome = orchestrationService.decideAndRoute(request);
        ClaimStateService.State state = claimStateService.getState(outcome.getClaimId());
        List<TaskResult> tasks = taskManagementService.getTasks(outcome.getClaimId());
        boolean diariesInitialized = diaryService.initializeDiaries(outcome.getClaimId());

        // Verify Expected Results
        assertNotNull(outcome.getClaimId(), "Claim number must be generated");
        assertTrue(outcome.getClaimId().matches("CLM-FL01-2024-\\d{6}"), "Claim number must match CLM-FL01-YYYY-NNNNNN format");
        assertEquals("Claim Opened", state.name(), "State must transition to Claim Opened");
        assertEquals("Fast-track claim", outcome.getClaimType(), "Claim type must be assigned as Fast-track claim");

        List<String> taskNames = tasks.stream().map(TaskResult::getName).collect(Collectors.toList());
        assertTrue(taskNames.contains("Review FNOL"), "Task 'Review FNOL' must exist");
        assertTrue(taskNames.contains("Acknowledge Claim"), "Task 'Acknowledge Claim' must exist");
        assertTrue(taskNames.contains("Assign Adjuster"), "Task 'Assign Adjuster' must exist");

        assertFalse(taskNames.contains("SIU Review"), "No SIU review task should be generated");
        assertFalse(taskNames.contains("Coverage Review"), "No Coverage review task should be generated");

        assertTrue(diariesInitialized, "Statutory diaries must be initialized");
    }
}
