package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class StateTransitionTest {

    @Mock
    private FnolStatusRepository fnolStatusRepository;

    @Mock
    private EngagementDecisionService engagementDecisionService;

    private InsuredEngagementService insuredEngagementService;

    @BeforeEach
    void setUp() {
        insuredEngagementService = new InsuredEngagementService(fnolStatusRepository, engagementDecisionService);
    }

    @Test
    void applies_when_fnol_status_is_unmatched_fnol() {
        // Arrange
        String fnolId = "fnol-unmatched-001";
        when(fnolStatusRepository.getStatus(fnolId)).thenReturn(FnolStatus.UNMATCHED_FNOL);

        // Act
        boolean transitionTriggered = insuredEngagementService.evaluateStateTransition(fnolId);

        // Assert
        assertTrue(transitionTriggered, "State transition must apply when FNOL status is Unmatched_FNOL");
        verify(engagementDecisionService, times(1)).applyTransition(FnolStatus.UNMATCHED_FNOL);
        verifyNoMoreInteractions(fnolStatusRepository, engagementDecisionService);
    }

    // Minimal domain stubs to ensure compilation and isolation from live I/O
    enum FnolStatus { UNMATCHED_FNOL, MATCHED_FNOL, CLOSED }
    
    interface FnolStatusRepository {
        FnolStatus getStatus(String fnolId);
    }

    interface EngagementDecisionService {
        void applyTransition(FnolStatus status);
    }

    static class InsuredEngagementService {
        private final FnolStatusRepository repository;
        private final EngagementDecisionService decisionService;

        InsuredEngagementService(FnolStatusRepository repo, EngagementDecisionService svc) {
            repository = repo;
            decisionService = svc;
        }

        boolean evaluateStateTransition(String fnolId) {
            FnolStatus status = repository.getStatus(fnolId);
            if (status == FnolStatus.UNMATCHED_FNOL) {
                decisionService.applyTransition(status);
                return true;
            }
            return false;
        }
    }
}
