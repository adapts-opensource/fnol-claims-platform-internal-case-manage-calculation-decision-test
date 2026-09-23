package app.integration.e2e;

import app.models.FnoLRequest;
import app.models.FnoLResponse;
import app.models.StatutoryDiary;
import app.models.Task;
import app.services.ClaimService;
import app.services.DiaryService;
import app.services.OrchestrationService;
import app.services.PolicyService;
import app.services.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class StandardFnolIntakeTriageE2eTest {

    private OrchestrationService orchestrationService;
    private ClaimService claimService;
    private TaskService taskService;
    private DiaryService diaryService;
    private PolicyService policyService;

    @BeforeEach
    void setUp() {
        // Initialize real application services.
        // Configured to point to local/test endpoints and databases for E2E execution.
        // No mocks, fakes, or patches are used per E2E rules.
        orchestrationService = new OrchestrationService();
        claimService = new ClaimService();
        taskService = new TaskService();
        diaryService = new DiaryService();
        policyService = new PolicyService();
    }

    @Test
    void orchestrateStandardFnolIntakeAndTriage() {
        // Build inputs from test-case Inputs / Expected Results and the Constants JSON sidecar
        FnoLRequest request = new FnoLRequest.Builder()
                .policyNumber("POL-8842-FL")
                .riskAddress("123 Palm Ave Miami FL 33101")
                .namedInsured("John Doe")
                .dateOfLoss(OffsetDateTime.parse("2024-05-10T14:30:00Z"))
                .causeOfLoss("Wind")
                .damageDescription("Roof shingle damage")
                .reporterType("Named Insured")
                .preferredCommunication("Email")
                .channel("Insured Portal")
                .build();

        // Execute orchestration against real services
        FnoLResponse response = orchestrationService.intakeAndTriage(request);

        // Assert Expected Results: Claim number is generated and returned in response
        assertNotNull(response.getClaimNumber(), "Claim number must be generated and returned in response");

        // Assert Expected Results: policy match status is Active
        assertEquals("Active", response.getPolicyMatchStatus(), "Policy match status must be Active");

        // Assert Expected Results: claim status is set to Open
        assertEquals("Open", response.getClaimStatus(), "Claim status must be set to Open");

        // Assert Expected Results: tasks Review FNOL, Acknowledge Claim, Assign Adjuster, and Contact Insured are created
        List<Task> tasks = taskService.findByClaimId(response.getClaimNumber());
        assertEquals(4, tasks.size(), "Exactly four standard tasks must be created");
        Set<String> taskNames = tasks.stream().map(Task::getTitle).collect(Collectors.toSet());
        assertTrue(taskNames.contains("Review FNOL"), "Task 'Review FNOL' must be created");
        assertTrue(taskNames.contains("Acknowledge Claim"), "Task 'Acknowledge Claim' must be created");
        assertTrue(taskNames.contains("Assign Adjuster"), "Task 'Assign Adjuster' must be created");
        assertTrue(taskNames.contains("Contact Insured"), "Task 'Contact Insured' must be created");

        // Assert Expected Results: statutory diaries for Claim Acknowledgment Due and Coverage Payment Due are created
        List<StatutoryDiary> diaries = diaryService.findByClaimId(response.getClaimNumber());
        assertEquals(2, diaries.size(), "Exactly two statutory diaries must be created");
        Set<String> diaryTitles = diaries.stream().map(StatutoryDiary::getTitle).collect(Collectors.toSet());
        assertTrue(diaryTitles.contains("Claim Acknowledgment Due"), "Diary 'Claim Acknowledgment Due' must be created");
        assertTrue(diaryTitles.contains("Coverage Payment Due"), "Diary 'Coverage Payment Due' must be created");

        // Assert Expected Results: initial triage severity is assigned based on cause of loss and damage description
        assertNotNull(response.getTriageSeverity(), "Initial triage severity must be assigned");
        assertFalse(response.getTriageSeverity().isBlank(), "Triage severity must not be blank");
    }
}
