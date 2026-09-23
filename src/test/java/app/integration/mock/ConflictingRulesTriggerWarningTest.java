package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentMatchers;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

// Mocked domain interfaces simulating external rule engine and warning service
interface RuleConflictValidator {
    boolean validateStateTransition(String claimId, String fromState, String toState);
}

interface WarningPublisher {
    void publishWarning(String warningCode, String message);
}

class StateTransitionOrchestrator {
    private final RuleConflictValidator conflictValidator;
    private final WarningPublisher warningPublisher;

    StateTransitionOrchestrator(RuleConflictValidator conflictValidator, WarningPublisher warningPublisher) {
        this.conflictValidator = conflictValidator;
        this.warningPublisher = warningPublisher;
    }

    void transitionState(String claimId, String fromState, String toState) {
        if (conflictValidator.validateStateTransition(claimId, fromState, toState)) {
            warningPublisher.publishWarning("CONFLICTING_RULES", "Conflicting rules detected during state transition from " + fromState + " to " + toState);
            return;
        }
        // Proceed with state transition logic...
    }
}

public class StateTransitionConflictWarningTest {

    @Mock
    private RuleConflictValidator conflictValidator;

    @Mock
    private WarningPublisher warningPublisher;

    private StateTransitionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new StateTransitionOrchestrator(conflictValidator, warningPublisher);
    }

    @Test
    void conflicting_rules_trigger_warning() {
        String claimId = "CLM-INS-9876";
        String currentState = "RECEIVED";
        String nextState = "UNDER_REVIEW";

        // Arrange: Simulate conflicting rules detection
        when(conflictValidator.validateStateTransition(claimId, currentState, nextState)).thenReturn(true);

        // Act: Execute state transition
        orchestrator.transitionState(claimId, currentState, nextState);

        // Assert: Verify warning was triggered exactly once with correct parameters
        verify(warningPublisher, times(1)).publishWarning(
                eq("CONFLICTING_RULES"),
                ArgumentMatchers.contains("Conflicting rules detected")
        );
    }
}
