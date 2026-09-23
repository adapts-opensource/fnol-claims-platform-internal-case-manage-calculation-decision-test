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
class ManualMatchOverridesAutomatedScoreTest {

    @Mock
    private AutomatedScoringService automatedScoringService;

    @Mock
    private MatchService matchService;

    @Mock
    private StateTransitionService stateTransitionService;

    @InjectMocks
    private InsuredEngagementService insuredEngagementService;

    private static final String INSURED_ID = "INS-12345";
    private static final String CLAIM_ID = "CLM-67890";
    private static final double AUTOMATED_SCORE = 75.0;
    private static final double MANUAL_SCORE = 88.5;
    private static final String INITIAL_STATE = "PENDING_REVIEW";
    private static final String EXPECTED_STATE = "MATCHED_AND_SCORED";

    @BeforeEach
    void setUp() {
        lenient().when(automatedScoringService.calculateScore(eq(INSURED_ID), eq(CLAIM_ID)))
                .thenReturn(AUTOMATED_SCORE);
        lenient().when(matchService.isManualOverride(eq(INSURED_ID), eq(CLAIM_ID)))
                .thenReturn(true);
        lenient().when(matchService.getManualMatchScore(eq(INSURED_ID), eq(CLAIM_ID)))
                .thenReturn(MANUAL_SCORE);
        lenient().when(stateTransitionService.transitionTo(eq(INITIAL_STATE), eq(EXPECTED_STATE)))
                .thenReturn(EXPECTED_STATE);
    }

    @Test
    void manualMatchOverridesAutomatedScore() {
        // Arrange: Simulate manual match detection and score retrieval
        when(matchService.isManualOverride(INSURED_ID, CLAIM_ID)).thenReturn(true);
        when(matchService.getManualMatchScore(INSURED_ID, CLAIM_ID)).thenReturn(MANUAL_SCORE);

        // Act: Execute the state transition logic
        String resultState = insuredEngagementService.processDecisionStateTransition(INSURED_ID, CLAIM_ID);

        // Assert: Verify state transitioned correctly
        assertEquals(EXPECTED_STATE, resultState, "State should transition to MATCHED_AND_SCORED");

        // Assert: Verify automated score was fetched but overridden by manual score
        verify(automatedScoringService, times(1)).calculateScore(INSURED_ID, CLAIM_ID);
        verify(matchService, times(1)).isManualOverride(INSURED_ID, CLAIM_ID);
        verify(matchService, times(1)).getManualMatchScore(INSURED_ID, CLAIM_ID);

        // Assert: Verify the transition service was called with the correct parameters
        verify(stateTransitionService, times(1)).transitionTo(INITIAL_STATE, EXPECTED_STATE);
    }
}
