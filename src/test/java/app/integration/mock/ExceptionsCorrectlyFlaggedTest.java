package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementStateTransitionTest {

    @Mock
    private EngagementStateRepository mockRepository;

    @Test
    void exceptionsCorrectlyFlagged() {
        // Arrange
        String insuredId = "INS-4582";
        String currentState = "CLAIM_SUBMITTED";
        String nextState = "UNDER_REVIEW";

        // Simulate external persistence or validation failure during transition
        doThrow(new IllegalArgumentException("State transition not permitted: CLAIM_SUBMITTED -> UNDER_REVIEW"))
                .when(mockRepository).updateState(eq(insuredId), eq(currentState), eq(nextState));

        EngagementStateTransitionService service = new EngagementStateTransitionService(mockRepository);

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            service.transitionState(insuredId, currentState, nextState);
        });

        // Verify exception message contains context for observability & debugging
        assertTrue(exception.getMessage().contains("State transition not permitted"));
    }
}

// Supporting domain contracts for mock isolation
interface EngagementStateRepository {
    void updateState(String insuredId, String currentState, String nextState);
}

class EngagementStateTransitionService {
    private final EngagementStateRepository repository;

    public EngagementStateTransitionService(EngagementStateRepository repository) {
        this.repository = repository;
    }

    public void transitionState(String insuredId, String currentState, String nextState) {
        repository.updateState(insuredId, currentState, nextState);
    }
}
