package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultipleMatchesTriggerManualTaskTest {

    @Mock
    private DecisionEvaluator decisionEvaluator;

    @Mock
    private ManualTaskDispatcher manualTaskDispatcher;

    @Mock
    private StateTransitionContext context;

    @InjectMocks
    private StateTransitionService stateTransitionService;

    @BeforeEach
    void setUp() {
        // MockitoExtension initializes mocks and injects them into @InjectMocks
    }

    @Test
    void multipleMatchesTriggerManualTask() {
        // Arrange
        String claimId = "CLM-MOCK-001";
        List<MatchResult> multipleMatches = List.of(
            new MatchResult("POL-001", 0.95),
            new MatchResult("POL-002", 0.93),
            new MatchResult("POL-003", 0.91)
        );

        when(context.getClaimId()).thenReturn(claimId);
        when(decisionEvaluator.evaluateMatches(anyString())).thenReturn(multipleMatches);

        // Act
        stateTransitionService.transition(context);

        // Assert
        verify(decisionEvaluator).evaluateMatches(claimId);
        verify(manualTaskDispatcher, times(1)).dispatch(
            eq(claimId),
            eq("MULTIPLE_MATCHES"),
            eq("Multiple insured policy matches detected. Requires manual adjudication."),
            eq("QUEUE_DEFAULT")
        );
        verifyNoMoreInteractions(decisionEvaluator, manualTaskDispatcher);
    }

    // Internal domain contracts for test isolation
    interface DecisionEvaluator {
        List<MatchResult> evaluateMatches(String entityId);
    }

    interface ManualTaskDispatcher {
        void dispatch(String claimId, String taskType, String description, String queue);
    }

    interface StateTransitionContext {
        String getClaimId();
    }

    record MatchResult(String policyId, double confidence) {}

    class StateTransitionService {
        private final DecisionEvaluator decisionEvaluator;
        private final ManualTaskDispatcher manualTaskDispatcher;

        StateTransitionService(DecisionEvaluator decisionEvaluator, ManualTaskDispatcher manualTaskDispatcher) {
            this.decisionEvaluator = decisionEvaluator;
            this.manualTaskDispatcher = manualTaskDispatcher;
        }

        void transition(StateTransitionContext context) {
            String claimId = context.getClaimId();
            List<MatchResult> matches = decisionEvaluator.evaluateMatches(claimId);
            if (matches.size() > 1) {
                manualTaskDispatcher.dispatch(
                    claimId,
                    "MULTIPLE_MATCHES",
                    "Multiple insured policy matches detected. Requires manual adjudication.",
                    "QUEUE_DEFAULT"
                );
            }
        }
    }
}
