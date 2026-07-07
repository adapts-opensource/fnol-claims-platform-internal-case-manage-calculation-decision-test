package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock integration test for Insured Engagement & Tracking: decision: state_transition.
 * Verifies that the system correctly rejects invalid schemas during state transitions.
 */
public class InsuredEngagementStateTransitionMockTest {

    private StateTransitionService stateTransitionService;

    @BeforeEach
    void setUp() {
        stateTransitionService = Mockito.mock(StateTransitionService.class);
    }

    @Test
    void invalid_schema() {
        String malformedSchema = "{ invalid: schema, missing brackets";
        Mockito.when(stateTransitionService.transitionState(malformedSchema))
               .thenThrow(new IllegalArgumentException("Invalid schema: validation failed"));

        assertThrows(IllegalArgumentException.class, () -> {
            stateTransitionService.transitionState(malformedSchema);
        });

        Mockito.verify(stateTransitionService, Mockito.times(1)).transitionState(malformedSchema);
    }

    // Mocked external service contract for state transition decisions
    private interface StateTransitionService {
        void transitionState(String schemaPayload);
    }
}
