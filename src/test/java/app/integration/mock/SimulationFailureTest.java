package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateTransitionSimulationFailureTest {

    @Mock
    private DecisionEngine decisionEngine;

    @Mock
    private EngagementPersistenceService engagementPersistenceService;

    @InjectMocks
    private InsuredEngagementService insuredEngagementService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and reset
    }

    @Test
    @DisplayName("SimulationFailure")
    void simulation_failure() {
        // Arrange
        String claimId = "CLM-SIM-FAIL-01";
        String currentState = "OPEN";
        String nextState = "UNDER_REVIEW";
        String failureReason = "Simulation rules validation failed";

        doThrow(new SimulationFailureException(failureReason))
                .when(decisionEngine)
                .simulateStateTransition(claimId, currentState, nextState);

        // Act & Assert
        SimulationFailureException thrown = assertThrows(
                SimulationFailureException.class,
                () -> insuredEngagementService.transitionState(claimId, currentState, nextState)
        );

        assertEquals(failureReason, thrown.getMessage());
        verify(decisionEngine, times(1)).simulateStateTransition(claimId, currentState, nextState);
        verifyNoInteractions(engagementPersistenceService);
    }

    // Minimal stubs for compilation context
    static class SimulationFailureException extends RuntimeException {
        SimulationFailureException(String message) { super(message); }
    }

    interface DecisionEngine {
        void simulateStateTransition(String claimId, String currentState, String nextState);
    }

    interface EngagementPersistenceService {
        void updateState(String claimId, String newState);
    }

    static class InsuredEngagementService {
        private final DecisionEngine decisionEngine;
        private final EngagementPersistenceService engagementPersistenceService;

        InsuredEngagementService(DecisionEngine decisionEngine, EngagementPersistenceService engagementPersistenceService) {
            this.decisionEngine = decisionEngine;
            this.engagementPersistenceService = engagementPersistenceService;
        }

        void transitionState(String claimId, String currentState, String nextState) {
            decisionEngine.simulateStateTransition(claimId, currentState, nextState);
            engagementPersistenceService.updateState(claimId, nextState);
        }
    }
}
