package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionNoMatchRuleUnintakeableExpectedOutcomeCreateUnmatchedTest {

    @Mock
    private DecisionRuleEvaluator decisionRuleEvaluator;

    @Mock
    private FnolShellRepository fnolShellRepository;

    @Mock
    private StateTransitionService stateTransitionService;

    private DecisionStateProcessor decisionStateProcessor;

    private static final String DECISION_NO_MATCH = "No match";
    private static final String RULE_UNINTAKEABLE = "Unintakeable";
    private static final String EXPECTED_STATE = "UNMATCHED_FNOL_SHELL";
    private static final String INCIDENT_ID = "inc-12345";

    @BeforeEach
    void setUp() {
        decisionStateProcessor = new DecisionStateProcessor(decisionRuleEvaluator, fnolShellRepository, stateTransitionService);
    }

    @Test
    void decision_no_match_rule_unintakeable_expected_outcome_create_unmatched_fnol_shell() {
        // Arrange
        when(decisionRuleEvaluator.evaluate(INCIDENT_ID))
                .thenReturn(Optional.of(new DecisionContext(DECISION_NO_MATCH, RULE_UNINTAKEABLE)));

        // Act
        decisionStateProcessor.processDecision(INCIDENT_ID);

        // Assert
        ArgumentCaptor<UnmatchedFnolShell> shellCaptor = ArgumentCaptor.forClass(UnmatchedFnolShell.class);
        verify(fnolShellRepository, times(1)).createUnmatchedShell(shellCaptor.capture());

        UnmatchedFnolShell capturedShell = shellCaptor.getValue();
        assertEquals(EXPECTED_STATE, capturedShell.getState());
        assertEquals(INCIDENT_ID, capturedShell.getIncidentId());

        verify(stateTransitionService, times(1)).transitionTo(EXPECTED_STATE, INCIDENT_ID);
        verifyNoMoreInteractions(fnolShellRepository, stateTransitionService, decisionRuleEvaluator);
    }
}
