package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AllDecisionsEmitExplainableAuditContextTest {

    @Mock
    private FnolOrchestrationService orchestrationService;

    @Mock
    private AuditContextEmitter auditContextEmitter;

    @Captor
    private ArgumentCaptor<ExplainableAuditContext> auditContextCaptor;

    @BeforeEach
    void setUp() {
        // Mocks initialized by MockitoExtension; no live infra calls
    }

    @Test
    void all_decisions_emit_explainable_audit_context() {
        // Arrange
        Map<String, Object> fnolPayload = Map.of(
                "id", "FNOL-2024-001",
                "channel", "WEB_PORTAL",
                "claimType", "AUTO",
                "incidentDate", "2024-05-20"
        );

        List<ValidationDecision> decisions = List.of(
                new ValidationDecision("DEC-001", DecisionType.ACCEPT, "Required fields validated successfully", Instant.now()),
                new ValidationDecision("DEC-002", DecisionType.REJECT, "Duplicate submission detected", Instant.now()),
                new ValidationDecision("DEC-003", DecisionType.PENDING, "Requires manual underwriting review", Instant.now())
        );

        doAnswer(invocation -> {
            auditContextEmitter.emitAuditContext(invocation.getArgument(0));
            return null;
        }).when(orchestrationService).executeValidation(anyMap(), anyList());

        // Act
        orchestrationService.executeValidation(fnolPayload, decisions);

        // Assert
        verify(auditContextEmitter, times(3)).emitAuditContext(auditContextCaptor.capture());
        List<ExplainableAuditContext> capturedContexts = auditContextCaptor.getAllValues();

        assertEquals(3, capturedContexts.size(), "All decisions must emit an audit context");
        for (ExplainableAuditContext context : capturedContexts) {
            assertNotNull(context.decisionId(), "Decision ID must be present");
            assertNotNull(context.decision(), "Decision type must be present");
            assertNotNull(context.reason(), "Explanation reason must be present");
            assertNotNull(context.timestamp(), "Timestamp must be present");
            assertNotNull(context.channel(), "Channel must be present");
            assertNotNull(context.payloadSnapshot(), "Payload snapshot must be present");
            assertFalse(context.reason().isBlank(), "Reason must not be blank for explainability");
            assertTrue(context.timestamp().isAfter(Instant.EPOCH), "Timestamp must be valid");
        }
    }

    // Test domain models representing feature contracts
    private record ValidationDecision(String decisionId, DecisionType decision, String reason, Instant timestamp) {}
    private record ExplainableAuditContext(String decisionId, DecisionType decision, String reason, Instant timestamp, String channel, Map<String, Object> payloadSnapshot) {}
}
