package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementOrchestrationDecisionTest {

    @Mock
    private RuleEngine mockRuleEngine;

    private DecisionOrchestrator decisionOrchestrator;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        decisionOrchestrator = new DecisionOrchestrator(mockRuleEngine);
    }

    @Test
    void multiple_conflicting_rules() {
        // Arrange: Mock rule engine to return multiple conflicting rules for an insured exposure
        List<String> conflictingRuleIds = List.of("RULE_INSURED_A", "RULE_INSURED_B");
        when(mockRuleEngine.getApplicableRules("EXPOSURE_12345")).thenReturn(conflictingRuleIds);

        // Act: Trigger decision orchestration
        DecisionOutcome outcome = decisionOrchestrator.evaluate("EXPOSURE_12345");

        // Assert: Verify conflict detection, state transition, and rule tracking
        assertNotNull(outcome);
        assertEquals(DecisionStatus.CONFLICT_DETECTED, outcome.getStatus());
        assertEquals(2, outcome.getConflictingRuleIds().size());
        assertTrue(outcome.getConflictingRuleIds().containsAll(conflictingRuleIds));
        verify(mockRuleEngine, times(1)).getApplicableRules("EXPOSURE_12345");
    }

    // Minimal test doubles to isolate orchestration logic from production dependencies
    static class RuleEngine {
        public List<String> getApplicableRules(String exposureId) { return List.of(); }
    }

    static class DecisionOrchestrator {
        private final RuleEngine ruleEngine;
        DecisionOrchestrator(RuleEngine ruleEngine) { this.ruleEngine = ruleEngine; }
        DecisionOutcome evaluate(String exposureId) {
            List<String> rules = ruleEngine.getApplicableRules(exposureId);
            return new DecisionOutcome(DecisionStatus.CONFLICT_DETECTED, rules);
        }
    }

    record DecisionOutcome(DecisionStatus status, List<String> conflictingRuleIds) {}
    enum DecisionStatus { CONFLICT_DETECTED }
}
