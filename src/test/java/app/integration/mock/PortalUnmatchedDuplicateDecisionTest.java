package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PortalUnmatchedDuplicateDecisionTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private DuplicateDetectionService duplicateDetectionService;

    @Mock
    private ClaimStateTransitionService claimStateTransitionService;

    @Mock
    private AcknowledgmentQueueService acknowledgmentQueueService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private FnolOrchestrationService fnolOrchestrationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection automatically
    }

    @Test
    void orchestrate_portal_unmatched_duplicate_fnol_decision() {
        // Arrange
        String submissionId = UUID.randomUUID().toString();
        Map<String, Object> inputPayload = Map.of(
                "channel", "Portal",
                "policyNumber", "POL-INVALID-999",
                "riskAddress", "123 Main St",
                "insuredName", "John Doe",
                "dateOfLoss", "2024-05-10",
                "causeOfLoss", "Water",
                "priorClaimExists", true,
                "priorClaimStatus", "Closed"
        );

        // Mock policy validation to fail
        when(policyValidationService.validatePolicy(anyString())).thenReturn(false);

        // Mock duplicate detection to return a match
        when(duplicateDetectionService.detectDuplicate(anyString(), anyString()))
                .thenReturn(new DuplicateDetectionResult("PRIOR-CLAIM-001", "Closed"));

        // Act
        fnolOrchestrationService.processSubmission(submissionId, inputPayload);

        // Assert
        // 1. Claim state transitions to Unmatched Policy
        verify(claimStateTransitionService).transitionState(eq(submissionId), eq("Unmatched Policy"));

        // 2. Duplicate detection task created for Review Potential Duplicate Claim
        verify(duplicateDetectionService).createReviewTask(eq(submissionId), eq("Review Potential Duplicate Claim"));

        // 3. Intake shell created with Unmatched FNOL status
        verify(claimStateTransitionService).createIntakeShell(eq(submissionId), eq("Unmatched FNOL"));

        // 4. Acknowledgment queued
        ArgumentCaptor<Map<String, Object>> ackPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(acknowledgmentQueueService).queueAcknowledgment(ackPayloadCaptor.capture());
        Map<String, Object> queuedAck = ackPayloadCaptor.getValue();
        assertNotNull(queuedAck, "Acknowledgment payload must not be null");
        assertTrue(queuedAck.containsKey("submissionId"), "Acknowledgment must reference submission ID");

        // 5. Audit log captures input validation and decision reasoning
        ArgumentCaptor<String> auditMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logEvent(anyString(), auditMessageCaptor.capture());
        String capturedAuditLog = auditMessageCaptor.getValue();
        assertTrue(capturedAuditLog.contains("Input validation"), "Audit log must capture input validation");
        assertTrue(capturedAuditLog.contains("Decision reasoning"), "Audit log must capture decision reasoning");
    }

    // Mock service interfaces for test isolation
    private interface PolicyValidationService { boolean validatePolicy(String policyNumber); }
    private interface DuplicateDetectionService {
        DuplicateDetectionResult detectDuplicate(String claimId, String insuredName);
        void createReviewTask(String claimId, String taskType);
    }
    private record DuplicateDetectionResult(String priorClaimId, String priorStatus) {}
    private interface ClaimStateTransitionService {
        void transitionState(String claimId, String newState);
        void createIntakeShell(String claimId, String status);
    }
    private interface AcknowledgmentQueueService { void queueAcknowledgment(Map<String, Object> payload); }
    private interface AuditLogService { void logEvent(String eventId, String message); }
    private interface FnolOrchestrationService { void processSubmission(String claimId, Map<String, Object> payload); }
}
