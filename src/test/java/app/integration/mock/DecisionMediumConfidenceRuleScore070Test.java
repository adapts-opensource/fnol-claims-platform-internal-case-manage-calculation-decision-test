package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.HashMap;

public class ClaimInitiationRoutingDecisionMockTest {

    // Simplified interfaces representing external I/O contracts
    interface DecisionRuleEngine {
        Map<String, Object> evaluate(Map<String, Object> payload);
    }

    interface TaskCreationService {
        void createTask(String claimId, String status);
    }

    interface CacheService {
        void put(String key, String value, int ttl);
    }

    // Service under test: orchestrates claim decision routing
    static class ClaimDecisionOrchestrator {
        private final DecisionRuleEngine ruleEngine;
        private final TaskCreationService taskService;
        private final CacheService cacheService;

        ClaimDecisionOrchestrator(DecisionRuleEngine ruleEngine, TaskCreationService taskService, CacheService cacheService) {
            this.ruleEngine = ruleEngine;
            this.taskService = taskService;
            this.cacheService = cacheService;
        }

        void routeClaim(Map<String, Object> payload) {
            Map<String, Object> decision = ruleEngine.evaluate(payload);
            String decisionLevel = (String) decision.get("decision");
            String outcome = (String) decision.get("expected_outcome");
            String claimId = (String) payload.get("id");

            if ("Medium Confidence".equals(decisionLevel)) {
                taskService.createTask(claimId, "held_for_review");
                cacheService.put("Cache & Reference Data:cache:" + claimId, outcome, 3600);
            }
        }
    }

    private ClaimDecisionOrchestrator orchestrator;
    private DecisionRuleEngine ruleEngine;
    private TaskCreationService taskService;
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        ruleEngine = mock(DecisionRuleEngine.class);
        taskService = mock(TaskCreationService.class);
        cacheService = mock(CacheService.class);
        orchestrator = new ClaimDecisionOrchestrator(ruleEngine, taskService, cacheService);
    }

    @Test
    void decision_medium_confidence_rule_score_0_7_0_9_expected_outcome_task_created_fnol_held_for_review() {
        // Given
        String claimId = "CLM-2024-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("ruleScore", 0.75); // Falls within 0.7 - 0.9 rule range

        Map<String, Object> expectedDecision = new HashMap<>();
        expectedDecision.put("decision", "Medium Confidence");
        expectedDecision.put("expected_outcome", "Task created; FNOL held for review.");
        when(ruleEngine.evaluate(anyMap())).thenReturn(expectedDecision);

        // When
        orchestrator.routeClaim(payload);

        // Then
        verify(taskService).createTask(eq(claimId), eq("held_for_review"));
        verify(cacheService).put(eq("Cache & Reference Data:cache:" + claimId), eq("Task created; FNOL held for review."), eq(3600));
    }
}
