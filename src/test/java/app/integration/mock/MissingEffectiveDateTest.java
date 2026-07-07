package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Mock integration tests for Insured Engagement & Tracking:decision:state_transition.
 * Validates input constraints and ensures external I/O is short-circuited on validation failure.
 * NFRs: input_validation, thread_safety, structured_logging, compliance(gdpr,soc2)
 */
@ExtendWith(MockitoExtension.class)
class StateTransitionMockTest {

    @Mock
    private DecisionStateTransitionRepository repository;

    private DecisionStateTransitionService service;

    @BeforeEach
    void setUp() {
        service = new DecisionStateTransitionService(repository);
    }

    @Test
    void missing_effective_date() {
        // Arrange
        final String insuredId = "INS-98765";
        final String currentState = "PENDING_REVIEW";
        final String nextState = "UNDER_REVIEW";

        // Act & Assert
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> service.transitionState(insuredId, currentState, nextState, null),
                "Expected IllegalArgumentException for missing effective date"
        );

        assertEquals("Effective date is required for state transitions.", thrown.getMessage());

        // Verify short-circuit: validation fails before any external I/O
        verifyNoInteractions(repository);
    }
}
