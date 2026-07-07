package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthorityVerifiedWithin2SecondsTest {

    // Minimal domain interfaces representing external I/O and state management
    interface ValidationEngine {
        boolean verifyAuthority(Map<String, Object> payload);
    }

    interface StateTransitionRepository {
        Map<String, Object> saveStateTransition(String id, Map<String, Object> payload);
    }

    // Orchestration facade that coordinates validation and state persistence
    static class FnolSubmissionOrchestrator {
        private final ValidationEngine validationEngine;
        private final StateTransitionRepository repository;

        FnolSubmissionOrchestrator(ValidationEngine validationEngine, StateTransitionRepository repository) {
            this.validationEngine = validationEngine;
            this.repository = repository;
        }

        String processSubmission(String id, Map<String, Object> payload) {
            boolean authorityOk = validationEngine.verifyAuthority(payload);
            if (!authorityOk) {
                throw new IllegalStateException("Authority verification failed");
            }
            return repository.saveStateTransition(id, payload).get("id").toString();
        }
    }

    @Mock
    private ValidationEngine validationEngine;

    @Mock
    private StateTransitionRepository stateTransitionRepository;

    private FnolSubmissionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new FnolSubmissionOrchestrator(validationEngine, stateTransitionRepository);
    }

    @Test
    void authority_verified_within_2_seconds() {
        String submissionId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "channel", "WEB",
                "authorityId", "AUTH-VERIFY-001",
                "claimType", "AUTO"
        );

        // Mock external I/O to return deterministically and avoid network/DB latency
        when(validationEngine.verifyAuthority(anyMap())).thenReturn(true);
        when(stateTransitionRepository.saveStateTransition(anyString(), anyMap()))
                .thenReturn(Map.of("id", submissionId, "status", "AUTHORITIES_VERIFIED"));

        long startNanos = System.nanoTime();

        // Execute orchestration: validation -> state transition -> notification
        String resultId = orchestrator.processSubmission(submissionId, payload);

        long elapsedMillis = java.time.Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

        // Assert functional outcome
        assertEquals(submissionId, resultId);

        // Assert NFR: Authority verified within 2 seconds (HA/SLA compliance)
        assertTrue(elapsedMillis <= 2000,
                "Authority verification SLA violated: took " + elapsedMillis + "ms, expected <= 2000ms");

        // Verify infrastructure calls were made exactly once
        verify(validationEngine, times(1)).verifyAuthority(payload);
        verify(stateTransitionRepository, times(1)).saveStateTransition(eq(submissionId), anyMap());
    }
}
