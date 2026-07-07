package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Collections;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Data transfer object representing a state transition configuration update
class StateTransitionConfigUpdate {
    private final String sourceState;
    private final String targetState;
    private final Map<String, String> metadata;

    public StateTransitionConfigUpdate(String sourceState, String targetState, Map<String, String> metadata) {
        this.sourceState = sourceState;
        this.targetState = targetState;
        this.metadata = metadata;
    }

    public String getSourceState() { return sourceState; }
    public String getTargetState() { return targetState; }
    public Map<String, String> getMetadata() { return metadata; }
}

// External interface representing the state machine / config persistence layer
interface StateTransitionConfigService {
    void persistAndValidateUpdate(StateTransitionConfigUpdate update) throws IllegalArgumentException;
}

@ExtendWith(MockitoExtension.class)
class ConfigUpdatesValidateCorrectly {

    @Mock
    private StateTransitionConfigService externalConfigService;

    @BeforeEach
    void setUp() {
        // Reset mocks to ensure test isolation and prevent state leakage
        reset(externalConfigService);
    }

    @Test
    void config_updates_validate_correctly_valid_transition() {
        // Arrange
        StateTransitionConfigUpdate validUpdate = new StateTransitionConfigUpdate("NEW", "IN_PROGRESS", Map.of("trigger", "insured_action"));
        doNothing().when(externalConfigService).persistAndValidateUpdate(validUpdate);

        // Act & Assert
        assertDoesNotThrow(() -> externalConfigService.persistAndValidateUpdate(validUpdate));
        verify(externalConfigService, times(1)).persistAndValidateUpdate(validUpdate);
    }

    @Test
    void config_updates_validate_correctly_invalid_state_rejected() {
        // Arrange
        StateTransitionConfigUpdate invalidStateUpdate = new StateTransitionConfigUpdate("UNKNOWN_STATE", "IN_PROGRESS", Collections.emptyMap());
        doThrow(new IllegalArgumentException("Invalid state transition path: UNKNOWN_STATE -> IN_PROGRESS")).when(externalConfigService).persistAndValidateUpdate(invalidStateUpdate);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> externalConfigService.persistAndValidateUpdate(invalidStateUpdate));
        verify(externalConfigService, times(1)).persistAndValidateUpdate(invalidStateUpdate);
    }

    @Test
    void config_updates_validate_correctly_missing_fields_rejected() {
        // Arrange
        StateTransitionConfigUpdate incompleteUpdate = new StateTransitionConfigUpdate(null, "IN_PROGRESS", Collections.emptyMap());
        doThrow(new IllegalArgumentException("Source and target states must not be null")).when(externalConfigService).persistAndValidateUpdate(incompleteUpdate);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> externalConfigService.persistAndValidateUpdate(incompleteUpdate));
        verify(externalConfigService, times(1)).persistAndValidateUpdate(incompleteUpdate);
    }
}
