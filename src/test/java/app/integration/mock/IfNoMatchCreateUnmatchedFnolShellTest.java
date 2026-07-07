package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IfNoMatchCreateUnmatchedFnolShellTest {

    @Mock
    private MatchingService matchingService;
    @Mock
    private FnolShellPersistenceService shellPersistenceService;
    @Mock
    private ReserveLineRepository reserveLineRepository;

    private InsuredEngagementOrchestration orchestrationEngine;

    @BeforeEach
    void setUp() {
        orchestrationEngine = new InsuredEngagementOrchestration(
            matchingService, shellPersistenceService, reserveLineRepository
        );
    }

    @Test
    void if_no_match_create_unmatched_fnol_shell() {
        // Arrange: Simulate no existing match in the system (external I/O mock)
        FnoLSubmission submission = new FnoLSubmission(
            "POL-98765", "Jane Smith", "vehicle_collision", Instant.now().toString()
        );
        when(matchingService.queryForExistingMatch(anyString())).thenReturn(Optional.empty());

        // Act: Trigger the orchestration decision logic
        DecisionContext result = orchestrationEngine.evaluate(submission);

        // Assert: Verify shell creation was triggered and no downstream reserve actions occurred
        verify(shellPersistenceService).saveUnmatchedShell(any(FnoLSubmission.class));
        assertEquals(DecisionOutcome.UNMATCHED_SHELL_CREATED, result.getOutcome());
        assertNotNull(result.getShellId());
        verifyNoInteractions(reserveLineRepository);
    }

    // --- Supporting Types for Compilation Context ---
    static class FnoLSubmission {
        final String policyNumber;
        final String insuredName;
        final String incidentType;
        final String timestamp;
        FnoLSubmission(String policyNumber, String insuredName, String incidentType, String timestamp) {
            this.policyNumber = policyNumber;
            this.insuredName = insuredName;
            this.incidentType = incidentType;
            this.timestamp = timestamp;
        }
    }

    static class DecisionContext {
        private final DecisionOutcome outcome;
        private final String shellId;
        DecisionContext(DecisionOutcome outcome, String shellId) {
            this.outcome = outcome;
            this.shellId = shellId;
        }
        DecisionOutcome getOutcome() { return outcome; }
        String getShellId() { return shellId; }
    }

    interface MatchingService {
        Optional<String> queryForExistingMatch(String policyNumber);
    }

    interface FnolShellPersistenceService {
        String saveUnmatchedShell(FnoLSubmission submission);
    }

    interface ReserveLineRepository {
        void createReserveLine(String exposureId, double amount, String currency);
    }
}
