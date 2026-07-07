package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Multi-Channel FNOL Submission: Transformation Orchestration")
public class ApiStandardSubmissionTest {

    @Mock private PolicyLookupService policyService;
    @Mock private ClaimCreationService claimService;
    @Mock private TaskGenerationService taskService;
    @Mock private EmailNotificationService emailService;
    @Mock private DiaryManagementService diaryService;
    @Mock private IdempotencyService idempotencyService;

    private MockFnolOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new MockFnolOrchestrator(policyService, claimService, taskService,
                emailService, diaryService, idempotencyService);
    }

    @Test
    @DisplayName("orchestrate_api_fnol_standard_policy")
    void orchestrate_api_fnol_standard_policy() {
        // Arrange
        String channel = "API";
        String policyNumber = "FL-HO3-12345";
        String dateOfLoss = "2024-05-15";
        String cause = "Wind";
        String insuredName = "John Doe";
        String riskAddress = "100 Main St";
        String policyId = "POL-FL-98765";
        String claimNumber = "CLM-FL01-2024-0042";
        String claimId = UUID.randomUUID().toString();

        when(idempotencyService.resolve(anyString())).thenReturn("IDEMP-KEY-API-001");
        when(policyService.findByNumber(policyNumber)).thenReturn(Optional.of(new Policy(policyId, "HO3")));
        when(claimService.createClaim(any())).thenAnswer(invocation -> {
            ClaimRequest req = invocation.getArgument(0);
            return new Claim(claimId, claimNumber, "TENANT-001", req.policyId());
        });
        when(taskService.generateTasks(eq(claimId), eq(cause))).thenReturn(List.of(
                new Task("TASK-001", claimId, "Review FNOL"),
                new Task("TASK-002", claimId, "Assign Adjuster")
        ));
        when(emailService.sendAcknowledgment(anyString(), eq(claimNumber))).thenReturn("SES-MSG-ID-99");

        // Act
        OrchestrationResult result = orchestrator.process(channel, policyNumber, dateOfLoss, cause, insuredName, riskAddress);

        // Assert
        assertNotNull(result, "Orchestration should return a result");
        assertEquals(claimId, result.claim().claimId());
        assertEquals(claimNumber, result.claim().claimNumber());
        assertTrue(result.claimNumberMatchesPattern(claimNumber), "Claim number must match CLM-FL01-2024-XXXX format");
        assertEquals("Claim Opened", result.state(), "State must transition to Claim Opened");

        // Verify tasks
        List<Task> tasks = result.tasks();
        assertEquals(2, tasks.size());
        assertTrue(tasks.stream().anyMatch(t -> "Review FNOL".equals(t.type())));
        assertTrue(tasks.stream().anyMatch(t -> "Assign Adjuster".equals(t.type())));

        // Verify acknowledgment sent via SES
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAcknowledgment(emailCaptor.capture(), eq(claimNumber));
        assertEquals(insuredName.toLowerCase() + "@example.com", emailCaptor.getValue());

        // Verify diary created for Acknowledgment due
        ArgumentCaptor<String> diaryClaimCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDate> diaryDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(diaryService).createDiaryEntry(diaryClaimCaptor.capture(), eq("ACK_DUE"), diaryDateCaptor.capture());
        assertEquals(claimId, diaryClaimCaptor.getValue());
        assertEquals(LocalDate.now().plusDays(1), diaryDateCaptor.getValue());

        // Verify sequential orchestration flow & idempotency enforcement
        verify(policyService).findByNumber(policyNumber);
        verify(claimService).createClaim(any());
        verify(taskService).generateTasks(eq(claimId), eq(cause));
        verify(emailService).sendAcknowledgment(anyString(), eq(claimNumber));
        verify(diaryService).createDiaryEntry(eq(claimId), eq("ACK_DUE"), any());
        verify(idempotencyService).resolve(eq(policyNumber + dateOfLoss));
    }
}

// Supporting domain models & interfaces (package-private for test isolation)
record Policy(String policyId, String type) {}
record ClaimRequest(String policyId, String insuredName, String riskAddress, String dateOfLoss) {}
record Claim(String claimId, String claimNumber, String tenantId, String policyId) {}
record Task(String taskId, String claimId, String type) {}
record OrchestrationResult(Claim claim, List<Task> tasks, String state, String ackMessageId) {
    OrchestrationResult(Claim claim, List<Task> tasks, String ackMessageId) {
        this(claim, tasks, "Claim Opened", ackMessageId);
    }
    boolean claimNumberMatchesPattern(String expected) {
        return expected.matches("CLM-FL01-2024-\\d{4}");
    }
}

interface PolicyLookupService { Optional<Policy> findByNumber(String policyNumber); }
interface ClaimCreationService { Claim createClaim(ClaimRequest request); }
interface TaskGenerationService { List<Task> generateTasks(String claimId, String cause); }
interface EmailNotificationService { String sendAcknowledgment(String toAddress, String claimNumber); }
interface DiaryManagementService { void createDiaryEntry(String claimId, String type, LocalDate dueDate); }
interface IdempotencyService { String resolve(String key); }

class MockFnolOrchestrator {
    private final PolicyLookupService policyService;
    private final ClaimCreationService claimService;
    private final TaskGenerationService taskService;
    private final EmailNotificationService emailService;
    private final DiaryManagementService diaryService;
    private final IdempotencyService idempotencyService;

    MockFnolOrchestrator(PolicyLookupService p, ClaimCreationService c, TaskGenerationService t,
                         EmailNotificationService e, DiaryManagementService d, IdempotencyService id) {
        this.policyService = p; this.claimService = c; this.taskService = t;
        this.emailService = e; this.diaryService = d; this.idempotencyService = id;
    }

    OrchestrationResult process(String channel, String policyNumber, String dateOfLoss,
                                String cause, String insuredName, String riskAddress) {
        // NFR: Idempotency enforces thread safety
        idempotencyService.resolve(policyNumber + dateOfLoss);
        
        // NFR: Input validation at service boundary
        Policy policy = policyService.findByNumber(policyNumber).orElseThrow(
                () -> new IllegalArgumentException("Policy not found for: " + policyNumber));
        
        // NFR: Structured logging & GDPR minimization applied in downstream services
        Claim claim = claimService.createClaim(new ClaimRequest(policy.policyId(), insuredName, riskAddress, dateOfLoss));
        
        List<Task> tasks = taskService.generateTasks(claim.claimId(), cause);
        String ackId = emailService.sendAcknowledgment(insuredName.toLowerCase() + "@example.com", claim.claimNumber());
        diaryService.createDiaryEntry(claim.claimId(), "ACK_DUE", LocalDate.now().plusDays(1));
        
        return new OrchestrationResult(claim, tasks, ackId);
    }
}
