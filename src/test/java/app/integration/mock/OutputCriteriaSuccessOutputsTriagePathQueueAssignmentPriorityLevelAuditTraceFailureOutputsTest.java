package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutputCriteriaSuccessOutputsTriagePathQueueAssignmentPriorityLevelAuditTraceFailureOutputsRuleEvaluationErrorQueueUnavailableTest {

    @Mock
    private DecisionOrchestrator decisionOrchestrator;
    @Mock
    private RuleEvaluator ruleEvaluator;
    @Mock
    private QueueService queueService;
    @Mock
    private AuditTracer auditTracer;

    private Map<String, Object> orchestrationContext;

    @BeforeEach
    void setUp() {
        orchestrationContext = new HashMap<>();
        orchestrationContext.put("claimId", "CLM-1001");
        orchestrationContext.put("insuredId", "INS-2002");
        orchestrationContext.put("productType", "AUTO");
        orchestrationContext.put("timestamp", Instant.now());
    }

    @Test
    void output_criteria_success_outputs_triage_path_queue_assignment_priority_level_audit_trace_failure_outputs_rule_evaluation_error_queue_unavailable() {
        // Arrange success outputs
        when(decisionOrchestrator.resolveTriagePath(any(Map.class))).thenReturn("HIGH_PRIORITY_CLAIMS");
        when(queueService.assignQueue(anyString())).thenReturn("AUTO_SPECIALIST_LANE");
        when(decisionOrchestrator.determinePriority(anyString())).thenReturn("P1_CRITICAL");
        when(auditTracer.captureDecisionTrace(anyString(), anyString())).thenReturn("TRACE-ID-8842");

        // Arrange failure outputs
        when(ruleEvaluator.evaluateBusinessRules(any())).thenThrow(new RuleEvaluationException("RULE_FAIL_401", "Invalid coverage matrix for new insured tier"));
        when(queueService.isAvailable(anyString())).thenReturn(false);

        // Act
        DecisionOutcome outcome = decisionOrchestrator.executeDecision(orchestrationContext);

        // Assert success outputs
        assertNotNull(outcome);
        assertEquals("HIGH_PRIORITY_CLAIMS", outcome.triagePath());
        assertEquals("AUTO_SPECIALIST_LANE", outcome.queueAssignment());
        assertEquals("P1_CRITICAL", outcome.priorityLevel());
        assertEquals("TRACE-ID-8842", outcome.auditTrace());

        // Assert failure outputs
        assertNotNull(outcome.ruleEvaluationError());
        assertEquals("RULE_FAIL_401", outcome.ruleEvaluationError().errorCode());
        assertEquals("Invalid coverage matrix for new insured tier", outcome.ruleEvaluationError().reason());
        assertTrue(outcome.queueUnavailable());

        // Verify orchestration flow interactions
        verify(decisionOrchestrator, times(1)).resolveTriagePath(any(Map.class));
        verify(queueService, times(1)).assignQueue(anyString());
        verify(decisionOrchestrator, times(1)).determinePriority(anyString());
        verify(auditTracer, times(1)).captureDecisionTrace(anyString(), anyString());
        verify(ruleEvaluator, times(1)).evaluateBusinessRules(any());
        verify(queueService, times(1)).isAvailable(anyString());
    }

    // Static inner types to keep the test self-contained
    record DecisionOutcome(
            String triagePath,
            String queueAssignment,
            String priorityLevel,
            String auditTrace,
            RuleEvaluationError ruleEvaluationError,
            boolean queueUnavailable
    ) {}

    record RuleEvaluationError(String errorCode, String reason) {}

    interface DecisionOrchestrator {
        String resolveTriagePath(Map<String, Object> context);
        String determinePriority(String triagePath);
        DecisionOutcome executeDecision(Map<String, Object> context);
    }

    interface RuleEvaluator {
        void evaluateBusinessRules(Map<String, Object> context) throws RuleEvaluationException;
    }

    interface QueueService {
        String assignQueue(String triagePath);
        boolean isAvailable(String queueId);
    }

    interface AuditTracer {
        String captureDecisionTrace(String claimId, String priorityLevel);
    }

    static class RuleEvaluationException extends RuntimeException {
        final String errorCode;
        final String reason;
        RuleEvaluationException(String errorCode, String reason) {
            super(reason);
            this.errorCode = errorCode;
            this.reason = reason;
        }
    }
}
