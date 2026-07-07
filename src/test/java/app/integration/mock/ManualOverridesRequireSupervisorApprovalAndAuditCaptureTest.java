package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ManualOverridesRequireSupervisorApprovalAndAuditCaptureTest {

    @Mock
    private SupervisorApprovalService supervisorApprovalService;

    @Mock
    private AuditCaptureService auditCaptureService;

    private DecisionTransformationService decisionTransformationService;

    @BeforeEach
    void setUp() {
        decisionTransformationService = new DecisionTransformationService(supervisorApprovalService, auditCaptureService);
    }

    @Test
    void manual_overrides_require_supervisor_approval_and_audit_capture() {
        // Arrange
        String overrideId = UUID.randomUUID().toString();
        String exposureId = "EXP-98765";
        String supervisorId = "SUPERVISOR-001";
        OverrideRequest request = new OverrideRequest(overrideId, exposureId, "CLAIM_AMOUNT_ADJUSTMENT", 1500.00, "USD");

        // Mock supervisor approval validation
        when(supervisorApprovalService.validateApproval(eq(overrideId), eq(supervisorId))).thenReturn(true);

        // Act
        TransformationResult result = decisionTransformationService.processOverride(request, supervisorId);

        // Assert: Override must succeed when supervisor validates
        assertTrue(result.isApproved(), "Override must be approved when supervisor validates");
        verify(supervisorApprovalService).validateApproval(overrideId, supervisorId);

        // Assert: Audit event must be captured with compliance-grade metadata
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditCaptureService).captureAudit(auditCaptor.capture());
        AuditEvent capturedAudit = auditCaptor.getValue();

        assertEquals(overrideId, capturedAudit.getReferenceId(), "Audit must reference the original override ID");
        assertEquals("MANUAL_OVERRIDE", capturedAudit.getEventType(), "Audit must capture event type");
        assertEquals(supervisorId, capturedAudit.getActor(), "Audit must capture approving supervisor");
        assertEquals("APPROVED", capturedAudit.getStatus(), "Audit must reflect approval status");
        assertTrue(capturedAudit.getTimestamp().isAfter(LocalDateTime.now().minusMinutes(1)), "Audit timestamp must be current");
    }

    // Minimal domain contracts for self-contained mock testing
    interface SupervisorApprovalService {
        boolean validateApproval(String overrideId, String supervisorId);
    }

    interface AuditCaptureService {
        void captureAudit(AuditEvent event);
    }

    record OverrideRequest(String overrideId, String exposureId, String adjustmentType, double amount, String currency) {}

    record TransformationResult(boolean approved) {
        boolean isApproved() { return approved; }
    }

    record AuditEvent(String referenceId, String eventType, String actor, String status, LocalDateTime timestamp) {}

    // Service under test delegating to mocked external I/O
    static class DecisionTransformationService {
        private final SupervisorApprovalService approvalService;
        private final AuditCaptureService auditService;

        DecisionTransformationService(SupervisorApprovalService approvalService, AuditCaptureService auditService) {
            this.approvalService = approvalService;
            this.auditService = auditService;
        }

        TransformationResult processOverride(OverrideRequest request, String supervisorId) {
            boolean isValid = approvalService.validateApproval(request.overrideId(), supervisorId);
            if (!isValid) {
                throw new IllegalStateException("Supervisor approval required for manual override");
            }
            auditService.captureAudit(new AuditEvent(
                request.overrideId(),
                "MANUAL_OVERRIDE",
                supervisorId,
                "APPROVED",
                LocalDateTime.now()
            ));
            return new TransformationResult(true);
        }
    }
}
