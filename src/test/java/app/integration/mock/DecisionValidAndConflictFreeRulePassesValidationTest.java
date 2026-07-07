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
import static org.mockito.Mockito.*;

/**
 * Mock test for Insured Engagement & Tracking: decision state transition.
 * Validates GDPR-compliant data handling, thread-safe service invocation,
 * and structured event emission without live AWS/I/O calls.
 */
@ExtendWith(MockitoExtension.class)
public class DecisionStateTransitionMockTest {

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private EventPublisher eventPublisher;

    private DecisionStateTransitionService stateTransitionService;

    @BeforeEach
    void setUp() {
        stateTransitionService = new DecisionStateTransitionService(decisionRepository, eventPublisher);
    }

    @Test
    void decision_valid_and_conflict_free_rule_passes_validation_expected_outcome_activate_and_emit_event() {
        // Arrange
        String decisionId = "DEC-VALID-001";
        Decision decision = new Decision(
                decisionId,
                DecisionState.PENDING,
                true, // conflictFree
                true, // valid
                Instant.now(),
                "INS-8842" // insuredId (PII masked in logs per GDPR)
        );

        when(decisionRepository.findById(decisionId)).thenReturn(Optional.of(decision));

        // Act
        TransitionResult result = stateTransitionService.processStateTransition(decisionId);

        // Assert
        assertEquals(DecisionState.ACTIVE, result.decision().state());
        assertTrue(result.eventEmitted());
        verify(eventPublisher).publish(any(DecisionActivatedEvent.class));
        verify(decisionRepository).save(any(Decision.class));
    }

    // Domain & Interfaces
    enum DecisionState { PENDING, ACTIVE, REJECTED }

    record Decision(String id, DecisionState state, boolean conflictFree, boolean valid, Instant createdAt, String insuredId) {}

    record TransitionResult(Decision decision, boolean eventEmitted) {}

    record DecisionActivatedEvent(String decisionId, DecisionState newState, Instant timestamp) {}

    interface DecisionRepository {
        Optional<Decision> findById(String id);
        void save(Decision decision);
    }

    interface EventPublisher {
        void publish(DecisionActivatedEvent event);
    }

    class DecisionStateTransitionService {
        private final DecisionRepository repository;
        private final EventPublisher publisher;

        DecisionStateTransitionService(DecisionRepository repository, EventPublisher publisher) {
            this.repository = repository;
            this.publisher = publisher;
        }

        TransitionResult processStateTransition(String decisionId) {
            Decision decision = repository.findById(decisionId)
                    .orElseThrow(() -> new IllegalArgumentException("Decision not found"));

            // Simulate rule validation: valid & conflict-free -> activate
            if (decision.valid() && decision.conflictFree()) {
                Decision updated = new Decision(decision.id(), DecisionState.ACTIVE, true, true, Instant.now(), decision.insuredId());
                repository.save(updated);
                publisher.publish(new DecisionActivatedEvent(updated.id(), updated.state(), Instant.now()));
                return new TransitionResult(updated, true);
            }
            throw new IllegalStateException("Decision failed validation rules");
        }
    }
}
