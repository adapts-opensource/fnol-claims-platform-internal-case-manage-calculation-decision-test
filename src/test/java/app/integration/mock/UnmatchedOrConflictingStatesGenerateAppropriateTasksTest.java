package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private ClaimDataStore claimDataStore;

    @Mock
    private StateTransitionValidator stateTransitionValidator;

    @Mock
    private TaskGenerationService taskGenerationService;

    @InjectMocks
    private ClaimDataStandardizationOrchestrator claimDataStandardizationOrchestrator;

    @Test
    void unmatched_or_conflicting_states_generate_appropriate_tasks() {
        String claimId = "CLM-98765";
        Map<String, Object> payload = Map.of("state", "PENDING_REVIEW", "status", "APPROVED");
        Map<String, Object> expectedTaskPayload = Map.of("type", "STATE_CONFLICT_RESOLUTION", "claimId", claimId, "priority", "HIGH");

        when(claimDataStore.fetchClaimData(eq(claimId))).thenReturn(Map.of("id", claimId, "payload", payload));
        when(stateTransitionValidator.validate(anyMap())).thenReturn(false);

        claimDataStandardizationOrchestrator.processStandardization(claimId);

        verify(taskGenerationService, times(1)).generateTask(eq(claimId), eq("STATE_CONFLICT_RESOLUTION"), eq(expectedTaskPayload));
    }
}
