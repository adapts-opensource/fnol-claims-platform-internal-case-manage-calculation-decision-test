package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionStateTransitionMockTest {

    @Mock
    private DecisionRuleEngine decisionRuleEngine;

    @Mock
    private StateTransitionRepository stateTransitionRepository;

    @InjectMocks
    private DecisionStateTransitionService decisionStateTransitionService;

    @BeforeEach
    void setUp() {
        // Test environment initialized with mocked dependencies
    }

    @Test
    @DisplayName("decision_conflict_detected_rule_overlapping_dates_or_contradictory_logic_expected_outcome_reject_and_return_conflict_details")
    void decision_conflict_detected_rule_overlapping_dates_or_contradictory_logic_expected_outcome_reject_and_return_conflict_details() {
        // Arrange
        String insuredId = "INS-789012";
        LocalDate effectiveDate = LocalDate.of(2024, 11, 1);
        LocalDate expirationDate = LocalDate.of(2024, 11, 30);
        DecisionRequest request = new DecisionRequest(insuredId, effectiveDate, expirationDate);

        List<String> conflictDetails = List.of(
                "Effective date overlaps with active policy DEC-554",
                "Expiration date contradicts coverage term limits"
        );
        DecisionConflictException conflictException = new DecisionConflictException(
                "Conflict detected",
                "Overlapping dates or contradictory logic",
                conflictDetails
        );

        when(decisionRuleEngine.evaluateConflicts(any(DecisionRequest.class)))
                .thenThrow(conflictException);

        // Act & Assert
        DecisionConflictException thrown = assertThrows(
                DecisionConflictException.class,
                () -> decisionStateTransitionService.transitionState(request)
        );

        assertEquals("Conflict detected", thrown.getMessage());
        assertEquals("Overlapping dates or contradictory logic", thrown.getRule());
        assertEquals(conflictDetails, thrown.getDetails());

        verify(decisionRuleEngine, times(1)).evaluateConflicts(request);
        verifyNoInteractions(stateTransitionRepository);
    }

    // Domain DTOs and Exceptions
    static class DecisionRequest {
        private final String insuredId;
        private final LocalDate effectiveDate;
        private final LocalDate expirationDate;

        DecisionRequest(String insuredId, LocalDate effectiveDate, LocalDate expirationDate) {
            this.insuredId = insuredId;
            this.effectiveDate = effectiveDate;
            this.expirationDate = expirationDate;
        }

        public String getInsuredId() { return insuredId; }
        public LocalDate getEffectiveDate() { return effectiveDate; }
        public LocalDate getExpirationDate() { return expirationDate; }
    }

    static class DecisionConflictException extends RuntimeException {
        private final String rule;
        private final List<String> details;

        DecisionConflictException(String message, String rule, List<String> details) {
            super(message);
            this.rule = rule;
            this.details = details;
        }

        public String getRule() { return rule; }
        public List<String> getDetails() { return details; }
    }

    // Mocked Interfaces/Dependencies
    interface DecisionRuleEngine {
        void evaluateConflicts(DecisionRequest request);
    }

    interface StateTransitionRepository {
        void saveTransition(String insuredId, String decisionId, String status);
    }

    // Service Under Test
    class DecisionStateTransitionService {
        private final DecisionRuleEngine decisionRuleEngine;
        private final StateTransitionRepository stateTransitionRepository;

        DecisionStateTransitionService(DecisionRuleEngine decisionRuleEngine, StateTransitionRepository stateTransitionRepository) {
            this.decisionRuleEngine = decisionRuleEngine;
            this.stateTransitionRepository = stateTransitionRepository;
        }

        void transitionState(DecisionRequest request) {
            // Input validation & NFR: thread_safety handled by immutable DTOs & mock isolation
            decisionRuleEngine.evaluateConflicts(request);
            stateTransitionRepository.saveTransition(request.getInsuredId(), "DEC-NEW", "APPROVED");
        }
    }
}
