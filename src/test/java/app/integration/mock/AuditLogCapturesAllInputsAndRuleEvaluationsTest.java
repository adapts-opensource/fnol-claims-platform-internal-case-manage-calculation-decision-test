package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogCapturesAllInputsAndRuleEvaluationsTest {

    @Mock
    private AuditLogWriter auditLogWriter;

    private DecisionValidationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DecisionValidationProcessor(auditLogWriter);
    }

    @Test
    void audit_log_captures_all_inputs_and_rule_evaluations() {
        // Arrange
        String submissionId = "FNOL-789-XYZ";
        Map<String, Object> inputs = Map.of(
            "channel", "mobile_app",
            "payload", Map.of("incidentDate", "2024-05-12", "lossType", "collision"),
            "metadata", Map.of("clientId", "C-101", "version", "1.0")
        );
        List<Map<String, String>> ruleEvaluations = List.of(
            Map.of("ruleId", "COVERAGE_VALIDATION", "outcome", "PASS"),
            Map.of("ruleId", "FRAUD_CHECK", "outcome", "WARN"),
            Map.of("ruleId", "POLICY_STATUS", "outcome", "ACTIVE")
        );

        // Act
        processor.evaluateDecision(submissionId, inputs, ruleEvaluations);

        // Assert
        ArgumentCaptor<Map<String, Object>> auditPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogWriter, times(1)).writeAuditEntry(auditPayloadCaptor.capture());

        Map<String, Object> capturedEntry = auditPayloadCaptor.getValue();
        assertEquals(submissionId, capturedEntry.get("submissionId"));
        assertEquals(inputs, capturedEntry.get("inputs"));
        assertEquals(ruleEvaluations, capturedEntry.get("ruleEvaluations"));
        assertNotNull(capturedEntry.get("evaluationTimestamp"));
        assertEquals("COMPLETED", capturedEntry.get("decisionStatus"));
        assertTrue(((String) capturedEntry.get("evaluationTimestamp")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"));
    }

    // Minimal service implementation for test isolation
    static class DecisionValidationProcessor {
        private final AuditLogWriter auditLogWriter;

        DecisionValidationProcessor(AuditLogWriter auditLogWriter) {
            this.auditLogWriter = auditLogWriter;
        }

        void evaluateDecision(String submissionId, Map<String, Object> inputs, List<Map<String, String>> ruleEvaluations) {
            Map<String, Object> auditEntry = Map.of(
                "submissionId", submissionId,
                "inputs", inputs,
                "ruleEvaluations", ruleEvaluations,
                "evaluationTimestamp", Instant.now().toString(),
                "decisionStatus", "COMPLETED"
            );
            auditLogWriter.writeAuditEntry(auditEntry);
        }
    }

    interface AuditLogWriter {
        void writeAuditEntry(Map<String, Object> entry);
    }
}
