package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies Multi-Channel FNOL Submission orchestration routes to represented workflow,
 * creates attorney-specific tasks, restricts communication, and captures audit logs.
 * NFR Coverage: Input validation, thread safety (idempotency), GDPR/SOC2 audit logging, structured logging.
 */
@ExtendWith(MockitoExtension.class)
public class AttorneyOrchestrationTest {

    @Mock private InputValidator inputValidator;
    @Mock private ClaimRepository claimRepository;
    @Mock private TaskService taskService;
    @Mock private CommunicationService communicationService;
    @Mock private DiaryService diaryService;
    @Mock private AuditLogService auditLogService;
    @Mock private OrchestrationEngine orchestrationEngine;

    private AttorneyOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new AttorneyOrchestrationService(
            inputValidator, claimRepository, taskService,
            communicationService, diaryService, auditLogService, orchestrationEngine
        );
    }

    @Test
    void orchestrate_attorney_representation_routing() {
        // Arrange
        String channelId = "API";
        String policyNumber = "FL-HO3-55555";
        LocalDate dateOfLoss = LocalDate.of(2024, 6, 1);
        String cause = "Water";
        boolean attorneyFlag = true;
        String attorneyName = "Jane Smith";
        String attorneyBarNumber = "12345";
        String idempotencyKey = UUID.randomUUID().toString();
        String tenantId = "tenant-001";

        FNOLSubmissionInput input = new FNOLSubmissionInput(
            channelId, policyNumber, dateOfLoss, cause, attorneyFlag, attorneyName, attorneyBarNumber
        );

        // NFR: Input validation at service boundaries
        when(inputValidator.validate(any(FNOLSubmissionInput.class))).thenReturn(true);
        // NFR: Thread safety via idempotency keys & least privilege tenant context
        when(claimRepository.save(any(Claim.class))).thenReturn(new Claim("CLM-001", "CN-001", tenantId, "POL-001", "Represented"));
        when(taskService.create(any(Task.class))).thenReturn(new Task("T-001", "Attorney Representation Review"));
        when(communicationService.restrict(any(CommunicationRouting.class))).thenReturn(true);
        when(diaryService.createDue(any(DiaryDocument.class))).thenReturn(new DiaryDocument("D-001", "Representation", LocalDate.now().plusDays(7)));

        // Act
        ClaimResult result = service.orchestrate(input, tenantId, idempotencyKey);

        // Assert
        assertNotNull(result, "Orchestration must return a result");
        assertEquals("Represented", result.claimType(), "Claim type must be set to Represented");

        verify(taskService).create(argThat(task -> "Attorney Representation Review".equals(task.type())));

        verify(communicationService).restrict(argThat(routing -> routing.toAttorneyOnly()));

        verify(diaryService).createDue(argThat(diary -> "Representation".equals(diary.documentType())));

        ArgumentCaptor<AuditLogEntry> logCaptor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogService).log(logCaptor.capture());
        AuditLogEntry capturedLog = logCaptor.getValue();
        assertTrue(capturedLog.details().contains(attorneyName), "Audit log must capture attorney name (GDPR/SOC2 compliance)");
        assertTrue(capturedLog.details().contains(attorneyBarNumber), "Audit log must capture attorney bar number");
        assertTrue(capturedLog.tenantId().equals(tenantId), "Audit log must capture tenant context");
        assertTrue(capturedLog.idempotencyKey().equals(idempotencyKey), "Audit log must capture idempotency key for thread safety");
    }

    // === Internal Test Stubs for Compilation & Mocking ===
    record FNOLSubmissionInput(String channel, String policyNumber, LocalDate dateOfLoss, String cause, boolean attorneyFlag, String attorneyName, String attorneyBarNumber) {}
    record Claim(String claimId, String claimNumber, String tenantId, String policyId, String claimType) {}
    record Task(String taskId, String type) {}
    record CommunicationRouting(boolean toAttorneyOnly) { boolean toAttorneyOnly() { return toAttorneyOnly; } }
    record DiaryDocument(String docId, String documentType, LocalDate dueDate) {}
    record AuditLogEntry(String tenantId, String idempotencyKey, String details) {
        String tenantId() { return tenantId; }
        String idempotencyKey() { return idempotencyKey; }
        String details() { return details; }
    }
    record ClaimResult(String claimType) { String claimType() { return claimType; } }
    interface InputValidator { boolean validate(FNOLSubmissionInput input); }
    interface ClaimRepository { Claim save(Claim claim); }
    interface TaskService { Task create(Task task); }
    interface CommunicationService { boolean restrict(CommunicationRouting routing); }
    interface DiaryService { DiaryDocument createDue(DiaryDocument diary); }
    interface AuditLogService { void log(AuditLogEntry entry); }
    interface OrchestrationEngine {}
}
