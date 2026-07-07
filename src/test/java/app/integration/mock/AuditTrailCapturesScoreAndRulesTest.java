package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditTrailCapturesScoreAndRulesTest {

    @Mock
    private AuditTrailService auditTrailService;

    @Mock
    private DecisionEngine decisionEngine;

    @InjectMocks
    private StateTransitionService stateTransitionService;

    @Test
    void audit_trail_captures_score_and_rules() {
        // Arrange
        String insuredId = "INS-9876";
        double expectedScore = 92.5;
        List<String> expectedRules = List.of("PRIORITY_ROUTING", "COMPLIANCE_CHECK");

        DecisionOutcome outcome = new DecisionOutcome(expectedScore, expectedRules);
        when(decisionEngine.evaluateDecision(anyString())).thenReturn(outcome);

        // Act
        stateTransitionService.processStateTransition(insuredId, TransitionState.SCORED);

        // Assert
        verify(auditTrailService, times(1)).recordAuditEvent(
            eq(insuredId),
            eq(TransitionState.SCORED),
            eq(expectedScore),
            eq(expectedRules)
        );
    }

    // Minimal domain model for test context
    private static class DecisionOutcome {
        private final double score;
        private final List<String> rules;

        public DecisionOutcome(double score, List<String> rules) {
            this.score = score;
            this.rules = rules;
        }

        public double getScore() {
            return score;
        }

        public List<String> getRules() {
            return rules;
        }
    }

    // Mock interfaces/enum placeholders for compilation context
    private enum TransitionState {
        SCORED
    }
    
    private interface AuditTrailService {
        void recordAuditEvent(String insuredId, TransitionState state, double score, List<String> rules);
    }
    
    private interface DecisionEngine {
        DecisionOutcome evaluateDecision(String insuredId);
    }
    
    private class StateTransitionService {
        public void processStateTransition(String insuredId, TransitionState state) {
            DecisionOutcome outcome = decisionEngine.evaluateDecision(insuredId);
            auditTrailService.recordAuditEvent(insuredId, state, outcome.getScore(), outcome.getRules());
        }
    }
}
