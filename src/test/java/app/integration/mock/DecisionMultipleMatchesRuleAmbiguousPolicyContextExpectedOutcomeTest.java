package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DecisionStateTransitionMockTest {

    enum DecisionOutcome { SINGLE_MATCH, MULTIPLE_MATCHES, NO_MATCH }
    enum ClaimState { NEW, AWAITING_POLICY_RESOLUTION, RESOLVED }

    interface PolicyMatcher {
        DecisionOutcome evaluate(String policyContext);
    }

    interface TaskCreator {
        void createResolvePolicyMatchTask(String claimId, String context);
    }

    interface StateTransitionService {
        void transitionTo(ClaimState newState, String reason);
    }

    static class DecisionStateProcessor {
        private final PolicyMatcher matcher;
        private final TaskCreator taskCreator;
        private final StateTransitionService stateTransitionService;

        DecisionStateProcessor(PolicyMatcher matcher, TaskCreator taskCreator, StateTransitionService stateTransitionService) {
            this.matcher = matcher;
            this.taskCreator = taskCreator;
            this.stateTransitionService = stateTransitionService;
        }

        void processDecision(String claimId, String policyContext) {
            DecisionOutcome outcome = matcher.evaluate(policyContext);
            if (outcome == DecisionOutcome.MULTIPLE_MATCHES) {
                taskCreator.createResolvePolicyMatchTask(claimId, policyContext);
                stateTransitionService.transitionTo(ClaimState.AWAITING_POLICY_RESOLUTION, "Multiple matches found");
            }
        }
    }

    @Mock
    private PolicyMatcher policyMatcher;

    @Mock
    private TaskCreator taskCreator;

    @Mock
    private StateTransitionService stateTransitionService;

    private DecisionStateProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new DecisionStateProcessor(policyMatcher, taskCreator, stateTransitionService);
    }

    @Test
    void decision_multiple_matches_rule_ambiguous_policy_context_expected_outcome_create_resolve_policy_match_task() {
        String claimId = "CLM-12345";
        String ambiguousContext = "POL-AMB-001";

        when(policyMatcher.evaluate(ambiguousContext)).thenReturn(DecisionOutcome.MULTIPLE_MATCHES);

        processor.processDecision(claimId, ambiguousContext);

        verify(policyMatcher, times(1)).evaluate(ambiguousContext);
        verify(taskCreator, times(1)).createResolvePolicyMatchTask(eq(claimId), eq(ambiguousContext));
        verify(stateTransitionService, times(1)).transitionTo(eq(ClaimState.AWAITING_POLICY_RESOLUTION), eq("Multiple matches found"));
        verifyNoMoreInteractions(policyMatcher, taskCreator, stateTransitionService);
    }
}
