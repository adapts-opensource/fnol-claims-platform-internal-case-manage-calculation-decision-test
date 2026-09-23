package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateTransitionDecisionEngineTest {

    @Mock
    private DecisionEvaluationService mockDecisionService;

    @Mock
    private StatePersistenceService mockStateService;

    private StateTransitionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new StateTransitionProcessor(mockDecisionService, mockStateService);
    }

    @Test
    void decision_low_similarity_rule_insufficient_overlap_expected_outcome_proceed_as_new_claim() {
        // Arrange
        String insuredId = "INS-2024-8842";
        DecisionOutcome lowSimilarityOutcome = new DecisionOutcome("Low similarity", "Insufficient overlap");

        when(mockDecisionService.evaluateSimilarityAndOverlap(anyString())).thenReturn(lowSimilarityOutcome);

        // Act
        TransitionResult result = processor.executeTransition(insuredId);

        // Assert
        assertEquals(TransitionOutcome.PROCEED_AS_NEW_CLAIM, result.getOutcome());
        verify(mockDecisionService).evaluateSimilarityAndOverlap(eq(insuredId));
        verify(mockStateService).persistState(eq(insuredId), eq(TransitionOutcome.PROCEED_AS_NEW_CLAIM));
    }

    // Domain contracts for isolated testing
    enum TransitionOutcome { PENDING, PROCEED_AS_NEW_CLAIM, MERGE_WITH_EXISTING }

    record DecisionOutcome(String decision, String rule) {}

    record TransitionResult(TransitionOutcome outcome) {}

    interface DecisionEvaluationService {
        DecisionOutcome evaluateSimilarityAndOverlap(String insuredId);
    }

    interface StatePersistenceService {
        void persistState(String insuredId, TransitionOutcome state);
    }

    static class StateTransitionProcessor {
        private final DecisionEvaluationService decisionService;
        private final StatePersistenceService stateService;

        StateTransitionProcessor(DecisionEvaluationService decisionService, StatePersistenceService stateService) {
            this.decisionService = decisionService;
            this.stateService = stateService;
        }

        TransitionResult executeTransition(String insuredId) {
            DecisionOutcome outcome = decisionService.evaluateSimilarityAndOverlap(insuredId);
            if ("Low similarity".equals(outcome.decision()) && "Insufficient overlap".equals(outcome.rule())) {
                TransitionOutcome nextState = TransitionOutcome.PROCEED_AS_NEW_CLAIM;
                stateService.persistState(insuredId, nextState);
                return new TransitionResult(nextState);
            }
            return new TransitionResult(TransitionOutcome.PENDING);
        }
    }
}
