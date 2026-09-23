package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimRoutingDecisionCalculationMockTest {

    @Mock
    private PriorityMatrixService priorityMatrixService;

    @Mock
    private RuleEngineService ruleEngineService;

    @InjectMocks
    private RoutingDecisionCalculator calculator;

    @BeforeEach
    void setUp() {
        // Mock setup is handled by MockitoExtension and @InjectMocks
    }

    @Test
    void conflict_resolution_follows_priority_matrix() {
        // Arrange: Simulate multiple conflicting routing rules for the same claim type
        Map<String, Object> claimPayload = Map.of("claimType", "AUTO", "damageAmount", 5000);
        
        List<RoutingRule> conflictingRules = List.of(
            new RoutingRule("RULE_GENERAL", "AUTO", 2, "DEPT_GENERAL"),
            new RoutingRule("RULE_SPECIALIZED", "AUTO", 5, "DEPT_SPECIALIZED"),
            new RoutingRule("RULE_STANDARD", "AUTO", 1, "DEPT_STANDARD")
        );

        // Mock external rule engine to return conflicting matches
        when(ruleEngineService.getMatchingRules(claimPayload)).thenReturn(conflictingRules);
        
        // Mock external priority matrix service defining resolution order
        when(priorityMatrixService.getPriorityMatrix("AUTO")).thenReturn(Map.of(
            "DEPT_GENERAL", 10,
            "DEPT_SPECIALIZED", 50,
            "DEPT_STANDARD", 20
        ));

        // Act: Trigger calculation
        RoutingDecision decision = calculator.calculate(claimPayload);

        // Assert: Verify highest priority rule wins conflict resolution
        assertNotNull(decision, "Routing decision must not be null");
        assertEquals("DEPT_SPECIALIZED", decision.getTarget(), "Should route to highest priority department");
        assertEquals("RULE_SPECIALIZED", decision.getSourceRuleId(), "Should trace back to the winning rule");
        
        // Verify external I/O was called exactly once
        verify(ruleEngineService, times(1)).getMatchingRules(claimPayload);
        verify(priorityMatrixService, times(1)).getPriorityMatrix("AUTO");
    }

    // --- Mocked External Dependencies & SUT ---

    interface PriorityMatrixService {
        Map<String, Integer> getPriorityMatrix(String claimType);
    }

    interface RuleEngineService {
        List<RoutingRule> getMatchingRules(Map<String, Object> payload);
    }

    record RoutingRule(String id, String claimType, int priority, String target) {}
    record RoutingDecision(String target, String sourceRuleId) {}

    static class RoutingDecisionCalculator {
        private final PriorityMatrixService matrixService;
        private final RuleEngineService ruleEngine;

        RoutingDecisionCalculator(PriorityMatrixService matrixService, RuleEngineService ruleEngine) {
            this.matrixService = matrixService;
            this.ruleEngine = ruleEngine;
        }

        RoutingDecision calculate(Map<String, Object> payload) {
            String claimType = (String) payload.get("claimType");
            List<RoutingRule> rules = ruleEngine.getMatchingRules(payload);
            Map<String, Integer> priorities = matrixService.getPriorityMatrix(claimType);

            RoutingRule bestRule = null;
            int maxPriority = Integer.MIN_VALUE;

            for (RoutingRule rule : rules) {
                int rulePriority = priorities.getOrDefault(rule.target(), 0);
                if (rulePriority > maxPriority) {
                    maxPriority = rulePriority;
                    bestRule = rule;
                }
            }
            if (bestRule == null) {
                throw new IllegalStateException("No matching rules found for claim type: " + claimType);
            }
            return new RoutingDecision(bestRule.target(), bestRule.id());
        }
    }
}
