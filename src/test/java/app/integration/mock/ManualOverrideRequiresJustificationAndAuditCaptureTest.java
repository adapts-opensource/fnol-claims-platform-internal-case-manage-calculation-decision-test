package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ManualOverrideRequiresJustificationAndAuditCaptureTest {

    @Mock
    private DecisionTransformationService transformationService;

    @Mock
    private AuditCaptureService auditService;

    private InsuredEngagementDecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        decisionEngine = new InsuredEngagementDecisionEngine(transformationService, auditService);
    }

    @Test
    void manual_override_requires_justification_and_audit_capture() {
        // Given
        String insuredId = "INS-789";
        String decisionId = "DEC-456";
        String overrideAction = "MANUAL_APPROVE";
        String justification = "Expedited processing per client request";

        // When
        decisionEngine.processManualOverride(insuredId, decisionId, overrideAction, justification);

        // Then
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(1)).capture(auditCaptor.capture());

        AuditEvent capturedAudit = auditCaptor.getValue();
        assertEquals(insuredId, capturedAudit.insuredId());
        assertEquals(decisionId, capturedAudit.decisionId());
        assertEquals(justification, capturedAudit.justification());
        assertEquals("MANUAL_OVERRIDE", capturedAudit.eventType());
        assertNotNull(capturedAudit.timestamp());
        assertEquals("system", capturedAudit.actor());

        verify(transformationService, times(1)).applyOverride(eq(insuredId), eq(decisionId), eq(overrideAction));
    }

    @Test
    void manual_override_fails_when_justification_is_missing() {
        // Given
        String insuredId = "INS-789";
        String decisionId = "DEC-456";
        String overrideAction = "MANUAL_REJECT";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            decisionEngine.processManualOverride(insuredId, decisionId, overrideAction, null);
        });

        verify(auditService, never()).capture(any());
        verify(transformationService, never()).applyOverride(any(), any(), any());
    }

    // Supporting interfaces/classes for the test to be self-contained
    interface DecisionTransformationService {
        void applyOverride(String insuredId, String decisionId, String action);
    }

    interface AuditCaptureService {
        void capture(AuditEvent event);
    }

    record AuditEvent(String insuredId, String decisionId, String eventType, String justification, String actor, LocalDateTime timestamp) {}
}
