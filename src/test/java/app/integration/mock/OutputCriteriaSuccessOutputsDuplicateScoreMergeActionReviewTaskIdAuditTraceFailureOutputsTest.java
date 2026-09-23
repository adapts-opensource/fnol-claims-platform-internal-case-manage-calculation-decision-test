package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Minimal domain types for self-contained mock test execution
record DecisionResult(Set<String> successOutputs, Set<String> failureOutputs) {}

interface QueryService { String execute(String claimId); }
interface ScoringEngineService { double evaluate(String claimId); }
interface MergeActionService { String process(String claimId); }
interface TaskTrackingService { String create(String claimId); }
interface AuditService { String record(String claimId); }

class DecisionOrchestrationService {
    private final QueryService queryService;
    private final ScoringEngineService scoringEngineService;
    private final MergeActionService mergeActionService;
    private final TaskTrackingService taskTrackingService;
    private final AuditService auditService;

    DecisionOrchestrationService(QueryService queryService, ScoringEngineService scoringEngineService,
                                 MergeActionService mergeActionService, TaskTrackingService taskTrackingService,
                                 AuditService auditService) {
        this.queryService = queryService;
        this.scoringEngineService = scoringEngineService;
        this.mergeActionService = mergeActionService;
        this.taskTrackingService = taskTrackingService;
        this.auditService = auditService;
    }

    DecisionResult makeDecision(String claimId) {
        Set<String> success = Set.of();
        Set<String> failure = Set.of();
        try {
            // Input validation & NFR: structured logging placeholder
            if (claimId == null || claimId.isBlank()) {
                throw new IllegalArgumentException("Invalid claim identifier");
            }
            queryService.execute(claimId);
            scoringEngineService.evaluate(claimId);
            mergeActionService.process(claimId);
            taskTrackingService.create(claimId);
            auditService.record(claimId);
            success = Set.of("duplicate_score", "merge_action", "review_task_id", "audit_trace");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("timeout")) {
                failure = Set.of("query_timeout");
            } else if (msg.contains("engine")) {
                failure = Set.of("scoring_engine_failure");
            } else {
                failure = Set.of("unknown_failure");
            }
        }
        return new DecisionResult(success, failure);
    }
}

@ExtendWith(MockitoExtension.class)
class InsuredEngagementDecisionOrchestrationMockTest {

    @Mock private QueryService queryService;
    @Mock private ScoringEngineService scoringEngineService;
    @Mock private MergeActionService mergeActionService;
    @Mock private TaskTrackingService taskTrackingService;
    @Mock private AuditService auditService;

    private DecisionOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new DecisionOrchestrationService(
                queryService, scoringEngineService, mergeActionService,
                taskTrackingService, auditService
        );
    }

    @Test
    void output_criteria_success_outputs_duplicate_score_merge_action_review_task_id_audit_trace_failure_outputs_query_timeout_scoring_engine_failure() {
        // --- Success Scenario ---
        when(queryService.execute(any())).thenReturn("ok");
        when(scoringEngineService.evaluate(any())).thenReturn(0.9);
        when(mergeActionService.process(any())).thenReturn("merged");
        when(taskTrackingService.create(any())).thenReturn("task-1");
        when(auditService.record(any())).thenReturn("trace-1");

        DecisionResult successResult = orchestrationService.makeDecision("CLAIM-SUCCESS");

        assertEquals(Set.of("duplicate_score", "merge_action", "review_task_id", "audit_trace"), successResult.successOutputs());
        assertTrue(successResult.failureOutputs().isEmpty());

        verify(queryService).execute(any());
        verify(scoringEngineService).evaluate(any());
        verify(mergeActionService).process(any());
        verify(taskTrackingService).create(any());
        verify(auditService).record(any());

        // --- Failure Scenario: Query Timeout ---
        when(queryService.execute(any())).thenThrow(new RuntimeException("Query timeout exceeded"));

        DecisionResult timeoutResult = orchestrationService.makeDecision("CLAIM-TIMEOUT");

        assertTrue(timeoutResult.successOutputs().isEmpty());
        assertEquals(Set.of("query_timeout"), timeoutResult.failureOutputs());

        verify(queryService, times(2)).execute(any());
        verify(scoringEngineService, never()).evaluate(any());
        verify(mergeActionService, never()).process(any());

        // --- Failure Scenario: Scoring Engine Failure ---
        reset(queryService, scoringEngineService, mergeActionService, taskTrackingService, auditService);
        when(queryService.execute(any())).thenReturn("ok");
        when(scoringEngineService.evaluate(any())).thenThrow(new RuntimeException("Scoring engine failure"));

        DecisionResult scoringResult = orchestrationService.makeDecision("CLAIM-SCORING-FAIL");

        assertTrue(scoringResult.successOutputs().isEmpty());
        assertEquals(Set.of("scoring_engine_failure"), scoringResult.failureOutputs());

        verify(scoringEngineService, times(2)).evaluate(any());
        verify(mergeActionService, never()).process(any());
        verify(taskTrackingService, never()).create(any());
        verify(auditService, never()).record(any());
    }
}
