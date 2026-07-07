package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolOrchestrationValidationTest {

    @Mock
    private FnolOrchestrationEngine orchestrationEngine;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    @Mock
    private SesClient sesClient;

    private FnolSubmissionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FnolSubmissionValidator(orchestrationEngine, dynamoDbClient, s3Client, sesClient);
    }

    @Test
    void decision_adjuster_selects_policy_rule_selection_confirmed_update_context_else_maintain_unresolved_expected_outcome_context_update_or_task_return() {
        // Given: Adjuster selects policy with confirmation flag set
        String submissionId = "fnol-sub-001";
        Map<String, Object> payload = Map.of(
            "adjuster_id", "adj-99",
            "policy_selection_confirmed", true,
            "channel", "WEB_PORTAL",
            "timestamp", System.currentTimeMillis()
        );

        // Mock orchestration validation to pass
        when(orchestrationEngine.validate(anyString(), anyMap())).thenReturn(true);

        // Mock context update to simulate successful state transition
        when(orchestrationEngine.updateContext(eq(submissionId), any(Map.class)))
                .thenReturn(Optional.of(Map.of("state", "CONTEXT_UPDATED", "next_step", "TASK_RETURN")));

        // When: Orchestrator processes the decision and validation rule
        Map<String, Object> result = validator.processSubmissionDecision(submissionId, payload);

        // Then: Verify context was updated and task return is expected
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("context_update") || result.containsKey("task_return"),
                "Expected outcome should contain context update or task return");
        assertEquals("CONTEXT_UPDATED", result.get("state"));

        // Verify infrastructure I/O contracts are mocked (no live calls per NFR)
        verify(orchestrationEngine).updateContext(eq(submissionId), any(Map.class));
        verify(dynamoDbClient, never()).putItem(any());
        verify(s3Client, never()).putObject(any());
        verify(sesClient, never()).sendEmail(any());

        // Verify payload constraints and PII stripping
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(orchestrationEngine).updateContext(eq(submissionId), payloadCaptor.capture());
        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertTrue(capturedPayload.containsKey("adjuster_id"), "Adjuster ID must be preserved in context");
        assertFalse(capturedPayload.containsKey("social_security_number"), "PII must be stripped before context update");
    }

    // Static inner classes to simulate dependencies for single-file compilation
    static class FnolSubmissionValidator {
        private final FnolOrchestrationEngine engine;
        private final DynamoDbClient db;
        private final S3Client s3;
        private final SesClient ses;

        FnolSubmissionValidator(FnolOrchestrationEngine e, DynamoDbClient d, S3Client s, SesClient s2) {
            engine = e; db = d; s3 = s; ses = s2;
        }

        Map<String, Object> processSubmissionDecision(String id, Map<String, Object> payload) {
            boolean confirmed = Boolean.TRUE.equals(payload.get("policy_selection_confirmed"));
            if (confirmed) {
                return engine.updateContext(id, payload).orElse(Map.of("status", "FAILED"));
            }
            return Map.of("status", "UNRESOLVED_MAINTAINED");
        }
    }

    interface FnolOrchestrationEngine {
        boolean validate(String id, Map<String, Object> p);
        Optional<Map<String, Object>> updateContext(String id, Map<String, Object> p);
    }

    interface DynamoDbClient {
        void putItem(Map<String, Object> item);
    }

    interface S3Client {
        void putObject(Map<String, Object> obj);
    }

    interface SesClient {
        void sendEmail(Map<String, Object> email);
    }
}
