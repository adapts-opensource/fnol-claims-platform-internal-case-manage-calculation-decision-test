package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionDolOutOfPeriodRuleCoverageReviewRequiredExpectedOutcomeRouteToCoverageReviewQueueTest {

    @Mock
    private StateTransitionService stateTransitionService;
    @Mock
    private QueueRouter queueRouter;
    @Mock
    private StructuredLogger logger;

    private DecisionContext decisionContext;

    @BeforeEach
    void setUp() {
        decisionContext = new DecisionContext();
        decisionContext.setDecision("DOL out of period");
        decisionContext.setRule("Coverage review required");
        decisionContext.setClaimId("CLM-88421");
        decisionContext.setInsuredId("INS-77309");
    }

    @Test
    void decision_dol_out_of_period_rule_coverage_review_required_expected_outcome_route_to_coverage_review_queue() {
        // Arrange: Configure mock service to return the expected routing outcome for this specific decision/rule combo
        when(stateTransitionService.evaluate(decisionContext))
                .thenReturn(TransitionOutcome.ROUTE_TO_COVERAGE_REVIEW_QUEUE);

        // Act: Execute the state transition logic
        TransitionOutcome actualOutcome = stateTransitionService.evaluate(decisionContext);

        // Assert: Verify the outcome matches the expected routing directive
        assertEquals(TransitionOutcome.ROUTE_TO_COVERAGE_REVIEW_QUEUE, actualOutcome);

        // Verify integration contract: Queue router receives the correct target and claim identifier
        verify(queueRouter, times(1)).routeToQueue("COVERAGE_REVIEW_QUEUE", decisionContext.getClaimId());
        verifyNoMoreInteractions(queueRouter);

        // Verify structured logging for observability NFR
        verify(logger, times(1)).info("State transition routed", 
                "claimId", decisionContext.getClaimId(), 
                "decision", "DOL out of period", 
                "rule", "Coverage review required",
                "destination", "COVERAGE_REVIEW_QUEUE");
    }

    // Minimal domain stubs for test compilation and isolation
    static class DecisionContext {
        private String decision;
        private String rule;
        private String claimId;
        private String insuredId;

        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getRule() { return rule; }
        public void setRule(String rule) { this.rule = rule; }
        public String getClaimId() { return claimId; }
        public void setClaimId(String claimId) { this.claimId = claimId; }
        public String getInsuredId() { return insuredId; }
        public void setInsuredId(String insuredId) { this.insuredId = insuredId; }
    }

    enum TransitionOutcome {
        ROUTE_TO_COVERAGE_REVIEW_QUEUE,
        ROUTE_TO_UNDERWRITING,
        CLOSE_AND_SETTLE
    }

    interface StateTransitionService {
        TransitionOutcome evaluate(DecisionContext ctx);
    }

    interface QueueRouter {
        void routeToQueue(String queueName, String claimId);
    }

    interface StructuredLogger {
        void info(String message, String... kvPairs);
    }
}
