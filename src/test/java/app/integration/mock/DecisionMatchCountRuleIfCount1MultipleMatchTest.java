package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionMatchCountRuleIfCount1MultipleMatchTest {

    @Mock
    private RoutingPathService routingPathService;
    @Mock
    private TaskGenerationService taskGenerationService;

    private DecisionValidationService decisionValidationService;

    @BeforeEach
    void setUp() {
        decisionValidationService = new DecisionValidationService(routingPathService, taskGenerationService);
    }

    @Test
    void decision_match_count_rule_if_count_1_multiple_match_if_count_1_single_match_if_count_0_unmatched_expected_outcome_routing_path_and_task_generation() {
        // Given: Match counts representing >1, ==1, ==0
        int[] matchCounts = {2, 1, 0};
        String[] expectedOutcomes = {"MULTIPLE_MATCH", "SINGLE_MATCH", "UNMATCHED"};

        for (int i = 0; i < matchCounts.length; i++) {
            // When: Evaluate decision based on match count
            DecisionOutcome outcome = decisionValidationService.evaluateMatchDecision(matchCounts[i]);
            String routingPath = decisionValidationService.resolveRoutingPath(outcome);
            String taskGeneration = decisionValidationService.resolveTaskGeneration(outcome);

            // Then: Verify decision rule, routing path, and task generation
            assertEquals(expectedOutcomes[i], outcome.name(), "Decision mismatch for count: " + matchCounts[i]);
            assertNotNull(routingPath, "Routing path must be generated for: " + expectedOutcomes[i]);
            assertNotNull(taskGeneration, "Task generation must be triggered for: " + expectedOutcomes[i]);

            // Verify external I/O mocks were called exactly once per scenario
            verify(routingPathService, times(1)).route(anyString(), anyString());
            verify(taskGenerationService, times(1)).generateTasks(anyString(), anyString());
        }
    }
}

enum DecisionOutcome { MULTIPLE_MATCH, SINGLE_MATCH, UNMATCHED }

interface RoutingPathService {
    void route(String tenantId, String decision);
}

interface TaskGenerationService {
    void generateTasks(String tenantId, String decision);
}

class DecisionValidationService {
    private final RoutingPathService routingPathService;
    private final TaskGenerationService taskGenerationService;

    DecisionValidationService(RoutingPathService routingPathService, TaskGenerationService taskGenerationService) {
        this.routingPathService = routingPathService;
        this.taskGenerationService = taskGenerationService;
    }

    DecisionOutcome evaluateMatchDecision(int matchCount) {
        if (matchCount > 1) return DecisionOutcome.MULTIPLE_MATCH;
        if (matchCount == 1) return DecisionOutcome.SINGLE_MATCH;
        return DecisionOutcome.UNMATCHED;
    }

    String resolveRoutingPath(DecisionOutcome outcome) {
        routingPathService.route("tenant_123", outcome.name());
        return outcome.name() + "_routing_path";
    }

    String resolveTaskGeneration(DecisionOutcome outcome) {
        taskGenerationService.generateTasks("tenant_123", outcome.name());
        return outcome.name() + "_task_gen";
    }
}
