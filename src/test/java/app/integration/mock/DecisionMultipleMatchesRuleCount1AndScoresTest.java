package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Minimal domain contracts for compilation context
record DecisionOutcome(String decision, boolean fnolHeld) {}
interface PolicyMatchValidator { List<Map<String, Object>> findMatches(String claimId); }
interface TaskRouter { void createTask(String taskType, String claimId); }
interface FnolStateManager { void updateState(String claimId, String state); }

class DecisionOrchestrator {
    private final PolicyMatchValidator validator;
    private final TaskRouter router;
    private final FnolStateManager manager;

    DecisionOrchestrator(PolicyMatchValidator validator, TaskRouter router, FnolStateManager manager) {
        this.validator = validator;
        this.router = router;
        this.manager = manager;
    }

    DecisionOutcome routeClaim(String claimId) {
        List<Map<String, Object>> matches = validator.findMatches(claimId);
        if (matches.size() > 1 && scoresWithinTolerance(matches, 5.0)) {
            router.createTask("Resolve Policy Match", claimId);
            manager.updateState(claimId, "FNOL_HELD");
            return new DecisionOutcome("Multiple Matches", true);
        }
        throw new UnsupportedOperationException("Unexpected routing path");
    }

    private boolean scoresWithinTolerance(List<Map<String, Object>> matches, double tolerance) {
        double min = matches.stream().mapToDouble(m -> ((Number) m.get("matchScore")).doubleValue()).min().orElse(0);
        double max = matches.stream().mapToDouble(m -> ((Number) m.get("matchScore")).doubleValue()).max().orElse(0);
        return (max - min) <= tolerance;
    }
}

@ExtendWith(MockitoExtension.class)
class ClaimDecisionOrchestrationMockTest {

    @Mock
    private PolicyMatchValidator matchValidator;
    @Mock
    private TaskRouter taskRouter;
    @Mock
    private FnolStateManager fnolStateManager;

    private DecisionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new DecisionOrchestrator(matchValidator, taskRouter, fnolStateManager);
    }

    @Test
    void decisionMultipleMatchesRuleCount1AndScoresWithinToleranceExpectedOutcomeTaskResolvePolicyMatchCreatedFnolHeld() {
        // Arrange
        String claimId = "CLM-789";
        List<Map<String, Object>> policyMatches = List.of(
            Map.of("policyId", "POL-A", "matchScore", 92.5),
            Map.of("policyId", "POL-B", "matchScore", 93.0)
        );

        when(matchValidator.findMatches(eq(claimId))).thenReturn(policyMatches);

        // Act
        DecisionOutcome outcome = orchestrator.routeClaim(claimId);

        // Assert
        assertEquals("Multiple Matches", outcome.decision());
        assertTrue(outcome.fnolHeld());
        verify(taskRouter).createTask(eq("Resolve Policy Match"), eq(claimId));
        verify(fnolStateManager).updateState(eq(claimId), eq("FNOL_HELD"));
    }
}
