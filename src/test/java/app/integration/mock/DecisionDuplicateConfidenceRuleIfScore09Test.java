package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionDuplicateConfidenceRuleMockTest {

    @Mock
    private Object communicationService;
    @Mock
    private Object dataPersistenceService;
    @Mock
    private Object documentMediaStoreService;

    @Test
    void decision_duplicate_confidence_rule_if_score_0_9_and_no_litigation_then_auto_merge_true() {
        double confidenceScore = 0.95;
        boolean hasLitigation = false;

        DecisionOutcome outcome = evaluateDuplicateConfidenceRule(confidenceScore, hasLitigation);

        assertTrue(outcome.isAutoMerge(), "Auto merge should be true for score >= 0.9 without litigation");
        assertFalse(outcome.isManualReview(), "Manual review should be false when auto merge is triggered");
        assertTrue(outcome.isMergeAction(), "Merge action should be enabled");
        assertFalse(outcome.isReviewTaskCreated(), "Review task should not be created on auto merge");
        verifyNoInteractions(communicationService, dataPersistenceService, documentMediaStoreService);
    }

    @Test
    void decision_duplicate_confidence_rule_if_0_7_score_0_9_then_manual_review_true() {
        double confidenceScore = 0.85;
        boolean hasLitigation = false;

        DecisionOutcome outcome = evaluateDuplicateConfidenceRule(confidenceScore, hasLitigation);

        assertFalse(outcome.isAutoMerge(), "Auto merge should be false for score < 0.9");
        assertTrue(outcome.isManualReview(), "Manual review should be true for score between 0.7 and 0.9");
        assertFalse(outcome.isMergeAction(), "Merge action should be disabled");
        assertTrue(outcome.isReviewTaskCreated(), "Review task should be created for manual review");
        verifyNoInteractions(communicationService, dataPersistenceService, documentMediaStoreService);
    }

    @Test
    void decision_duplicate_confidence_rule_else_manual_review_false() {
        double confidenceScore = 0.65;
        boolean hasLitigation = false;

        DecisionOutcome outcome = evaluateDuplicateConfidenceRule(confidenceScore, hasLitigation);

        assertFalse(outcome.isAutoMerge(), "Auto merge should be false for score < 0.7");
        assertFalse(outcome.isManualReview(), "Manual review should be false for score < 0.7");
        assertFalse(outcome.isMergeAction(), "Merge action should be disabled");
        assertFalse(outcome.isReviewTaskCreated(), "Review task should not be created");
        verifyNoInteractions(communicationService, dataPersistenceService, documentMediaStoreService);
    }

    private DecisionOutcome evaluateDuplicateConfidenceRule(double confidenceScore, boolean hasLitigation) {
        boolean autoMerge = confidenceScore >= 0.9 && !hasLitigation;
        boolean manualReview = !autoMerge && confidenceScore >= 0.7 && confidenceScore < 0.9;
        return new DecisionOutcome(autoMerge, manualReview, autoMerge, manualReview);
    }

    record DecisionOutcome(boolean autoMerge, boolean manualReview, boolean mergeAction, boolean reviewTaskCreated) {}
}
