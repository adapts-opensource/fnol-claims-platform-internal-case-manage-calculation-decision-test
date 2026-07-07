package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionStateTransitionMockTest {

    @Mock
    private DecisionValidationService validationService;

    @Mock
    private StateTransitionService stateTransitionService;

    @Mock
    private ExceptionReportGenerator exceptionReportGenerator;

    private DecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        decisionEngine = new DecisionEngine(validationService, stateTransitionService, exceptionReportGenerator);
    }

    @Test
    void decision_non_compliant_rule_missing_logs_or_outdated_rules_expected_outcome_flag_and_generate_exception_report() {
        // Arrange
        String claimId = "CLM-789";
        String ruleId = "RULE_LOGS_OR_RULES";
        boolean compliant = false; // Simulates missing logs or outdated rules

        when(validationService.isRuleCompliant(ruleId)).thenReturn(compliant);

        // Act
        TransitionResult result = decisionEngine.processDecision(claimId, ruleId);

        // Assert
        assertEquals(DecisionStatus.NON_COMPLIANT, result.getDecision());
        assertTrue(result.isFlagged());
        verify(exceptionReportGenerator).createReport(eq(claimId), any(ExceptionReport.class));
        verify(stateTransitionService).transitionTo(eq(claimId), eq(State.PENDING_REVIEW));
    }

    // --- Minimal Domain & Service Stubs for Compilation ---
    enum DecisionStatus { COMPLIANT, NON_COMPLIANT }
    enum State { ACTIVE, PENDING_REVIEW }

    interface DecisionValidationService {
        boolean isRuleCompliant(String ruleId);
    }
    interface StateTransitionService {
        void transitionTo(String claimId, State state);
    }
    interface ExceptionReportGenerator {
        void createReport(String claimId, ExceptionReport report);
    }
    record ExceptionReport(String claimId, String reason) {}
    record TransitionResult(DecisionStatus decision, boolean flagged) {}

    static class DecisionEngine {
        private final DecisionValidationService validationService;
        private final StateTransitionService stateTransitionService;
        private final ExceptionReportGenerator exceptionReportGenerator;

        DecisionEngine(DecisionValidationService vs, StateTransitionService sts, ExceptionReportGenerator erg) {
            this.validationService = vs;
            this.stateTransitionService = sts;
            this.exceptionReportGenerator = erg;
        }

        TransitionResult processDecision(String claimId, String ruleId) {
            boolean compliant = validationService.isRuleCompliant(ruleId);
            if (!compliant) {
                stateTransitionService.transitionTo(claimId, State.PENDING_REVIEW);
                exceptionReportGenerator.createReport(claimId, new ExceptionReport(claimId, "Missing logs or outdated rules"));
                return new TransitionResult(DecisionStatus.NON_COMPLIANT, true);
            }
            return new TransitionResult(DecisionStatus.COMPLIANT, false);
        }
    }
}
