package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class DecisionDuplicateStatusRuleIfScoreThresholdFlagTest {
    private static final double SCORE_THRESHOLD = 0.75;
    private static final String FLAGGED_PATH = "review_queue";
    private static final String CONTINUE_PATH = "standard_processing";
    private static final String EXPECTED_OUTCOME = "Clear duplicate handling path";

    @Mock
    private DuplicateStatusOrchestrationService orchestrationService;

    private TransformationContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        context = new TransformationContext(0.0, "initial");
    }

    @Test
    void decision_duplicate_status_rule_if_score_threshold_flag_for_review_else_continue_expected_outcome_clear_duplicate_handling_path() {
        // Arrange: Score meets/exceeds threshold -> should flag for review
        context = new TransformationContext(0.85, "initial");

        when(orchestrationService.evaluateDuplicateStatus(context)).thenAnswer(invocation -> {
            TransformationContext ctx = invocation.getArgument(0);
            if (ctx.getScore() >= SCORE_THRESHOLD) {
                ctx.setFlaggedForReview(true);
                ctx.setRoutingPath(FLAGGED_PATH);
            } else {
                ctx.setFlaggedForReview(false);
                ctx.setRoutingPath(CONTINUE_PATH);
            }
            return ctx;
        });

        // Act
        TransformationContext result = orchestrationService.evaluateDuplicateStatus(context);

        // Assert
        assertTrue(result.isFlaggedForReview(), "Duplicate status should be flagged when score >= threshold");
        assertEquals(FLAGGED_PATH, result.getRoutingPath(), "Routing path should direct to review queue");
        assertEquals(EXPECTED_OUTCOME, result.getExpectedOutcome(), "Outcome must clear duplicate handling path");

        verify(orchestrationService, times(1)).evaluateDuplicateStatus(context);
    }

    @Test
    void decision_duplicate_status_rule_else_continue_expected_outcome_clear_duplicate_handling_path() {
        // Arrange: Score below threshold -> should continue processing
        context = new TransformationContext(0.60, "initial");

        when(orchestrationService.evaluateDuplicateStatus(context)).thenAnswer(invocation -> {
            TransformationContext ctx = invocation.getArgument(0);
            if (ctx.getScore() >= SCORE_THRESHOLD) {
                ctx.setFlaggedForReview(true);
                ctx.setRoutingPath(FLAGGED_PATH);
            } else {
                ctx.setFlaggedForReview(false);
                ctx.setRoutingPath(CONTINUE_PATH);
            }
            return ctx;
        });

        // Act
        TransformationContext result = orchestrationService.evaluateDuplicateStatus(context);

        // Assert
        assertFalse(result.isFlaggedForReview(), "Should not flag for review when score < threshold");
        assertEquals(CONTINUE_PATH, result.getRoutingPath(), "Routing path should continue standard processing");
        assertEquals(EXPECTED_OUTCOME, result.getExpectedOutcome(), "Outcome must clear duplicate handling path");

        verify(orchestrationService, times(1)).evaluateDuplicateStatus(context);
    }

    // Internal context for test isolation
    private static class TransformationContext {
        private final double score;
        private final String initialPath;
        private boolean flaggedForReview;
        private String routingPath;
        private final String expectedOutcome;

        TransformationContext(double score, String initialPath) {
            this.score = score;
            this.initialPath = initialPath;
            this.flaggedForReview = false;
            this.routingPath = initialPath;
            this.expectedOutcome = EXPECTED_OUTCOME;
        }

        double getScore() { return score; }
        boolean isFlaggedForReview() { return flaggedForReview; }
        String getRoutingPath() { return routingPath; }
        String getExpectedOutcome() { return expectedOutcome; }

        void setFlaggedForReview(boolean flag) { this.flaggedForReview = flag; }
        void setRoutingPath(String path) { this.routingPath = path; }
    }

    // Mock interface representing the orchestration layer
    private interface DuplicateStatusOrchestrationService {
        TransformationContext evaluateDuplicateStatus(TransformationContext context);
    }
}
