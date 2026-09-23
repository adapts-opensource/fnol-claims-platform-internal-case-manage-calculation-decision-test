package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DecisionPolicyMatchResultRuleIfMatches1 {

    private PolicyMatchService matchService;
    private DecisionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        matchService = mock(PolicyMatchService.class);
        orchestrator = new DecisionOrchestrator(matchService);
    }

    @Test
    void decision_policy_match_result_rule_if_matches_1_then_match_found_true_else_if_matches_1_then_ambiguous_true_else_ambiguous_false_n_expected_outcome_match_found_ambiguous_or_unmatched() {
        // Scenario 1: matches == 1 -> match_found=true, ambiguous=false, outcome=MATCH_FOUND
        when(matchService.fetchMatchCount("INSURED-001")).thenReturn(1);
        DecisionResult result1 = orchestrator.evaluatePolicyMatchResult("INSURED-001");
        assertTrue(result1.isMatchFound(), "Expected match_found=true when matches == 1");
        assertFalse(result1.isAmbiguous(), "Expected ambiguous=false when matches == 1");
        assertEquals(DecisionOutcome.MATCH_FOUND, result1.getOutcome());

        // Scenario 2: matches > 1 -> match_found=false, ambiguous=true, outcome=AMBIGUOUS
        when(matchService.fetchMatchCount("INSURED-002")).thenReturn(3);
        DecisionResult result2 = orchestrator.evaluatePolicyMatchResult("INSURED-002");
        assertFalse(result2.isMatchFound(), "Expected match_found=false when matches > 1");
        assertTrue(result2.isAmbiguous(), "Expected ambiguous=true when matches > 1");
        assertEquals(DecisionOutcome.AMBIGUOUS, result2.getOutcome());

        // Scenario 3: matches == 0 -> match_found=false, ambiguous=false, outcome=UNMATCHED
        when(matchService.fetchMatchCount("INSURED-003")).thenReturn(0);
        DecisionResult result3 = orchestrator.evaluatePolicyMatchResult("INSURED-003");
        assertFalse(result3.isMatchFound(), "Expected match_found=false when matches == 0");
        assertFalse(result3.isAmbiguous(), "Expected ambiguous=false when matches == 0");
        assertEquals(DecisionOutcome.UNMATCHED, result3.getOutcome());
    }

    interface PolicyMatchService {
        int fetchMatchCount(String insuredId);
    }

    static class DecisionResult {
        private final boolean matchFound;
        private final boolean ambiguous;
        private final DecisionOutcome outcome;

        DecisionResult(boolean matchFound, boolean ambiguous, DecisionOutcome outcome) {
            this.matchFound = matchFound;
            this.ambiguous = ambiguous;
            this.outcome = outcome;
        }

        boolean isMatchFound() { return matchFound; }
        boolean isAmbiguous() { return ambiguous; }
        DecisionOutcome getOutcome() { return outcome; }
    }

    enum DecisionOutcome {
        MATCH_FOUND, AMBIGUOUS, UNMATCHED
    }

    class DecisionOrchestrator {
        private final PolicyMatchService matchService;

        DecisionOrchestrator(PolicyMatchService matchService) {
            this.matchService = matchService;
        }

        DecisionResult evaluatePolicyMatchResult(String insuredId) {
            int matches = matchService.fetchMatchCount(insuredId);
            if (matches == 1) {
                return new DecisionResult(true, false, DecisionOutcome.MATCH_FOUND);
            } else if (matches > 1) {
                return new DecisionResult(false, true, DecisionOutcome.AMBIGUOUS);
            } else {
                return new DecisionResult(false, false, DecisionOutcome.UNMATCHED);
            }
        }
    }
}
