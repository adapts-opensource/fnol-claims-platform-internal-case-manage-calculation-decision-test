package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementStateTransitionDecisionMockTest {

    @Mock
    private DecisionRuleEvaluator decisionRuleEvaluator;

    @InjectMocks
    private InsuredEngagementStateTransitionService stateTransitionService;

    @BeforeEach
    void setUp() {
        // Reset mocks and ensure clean state before each test
    }

    @Test
    void decision_single_match_found_rule_dol_within_period_and_no_restrictions_expected_outcome_proceed_to_triage() {
        // Arrange
        String claimId = "CLM-ENG-001";
        String matchStatus = "SINGLE_MATCH_FOUND";
        String dolStatus = "WITHIN_PERIOD";
        String restrictions = "NONE";

        when(decisionRuleEvaluator.evaluate(claimId, matchStatus, dolStatus, restrictions))
                .thenReturn(DecisionOutcome.PROCEED_TO_TRIAGE);

        // Act
        DecisionOutcome actualOutcome = stateTransitionService.transitionState(claimId, matchStatus, dolStatus, restrictions);

        // Assert
        assertEquals(DecisionOutcome.PROCEED_TO_TRIAGE, actualOutcome, "Expected state to transition to TRIAGE");
        verify(decisionRuleEvaluator, times(1)).evaluate(claimId, matchStatus, dolStatus, restrictions);
        verify(stateTransitionService, times(1)).recordAuditEvent(claimId, DecisionOutcome.PROCEED_TO_TRIAGE);
    }
}
