package app.integration.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration test for Insured Engagement & Tracking: Orchestration Decision.
 * 
 * NFR Compliance:
 * - GDPR: Uses synthetic mock identifiers; no real PII.
 * - Thread Safety: JUnit 5 + Mockito ensures isolated state per test.
 * - Security: Mocks prevent live AWS/HTTP calls; least privilege enforced by mock boundaries.
 * - Observability: Test structure supports structured logging assertions if logger mock is injected.
 */
@ExtendWith(MockitoExtension.class)
class InsuredEngagementDecisionMockTest {

    @Mock
    private ScoringService scoringService;

    @Mock
    private LitigationService litigationService;

    @Mock
    private MergeService mergeService;

    @InjectMocks
    private DecisionOrchestrationService decisionOrchestrationService;

    /**
     * Test Case Label: Score09TriggersAutoMergeIfNo
     * Description: Score >= 0.9 triggers auto-merge if no active litigation
     */
    @Test
    @DisplayName("Score09TriggersAutoMergeIfNo: Score >= 0.9 triggers auto-merge if no active litigation")
    void score_0_9_triggers_auto_merge_if_no_active_litigation() {
        // Arrange: Setup synthetic context
        String insuredId = "INS-MOCK-SYNTHETIC-001";
        String exposureId = "EXP-MOCK-SYNTHETIC-001";
        DecisionContext context = new DecisionContext(insuredId, exposureId);

        // NFR: Input Validation - Score is within valid range [0.0, 1.0]
        double score = 0.9;
        boolean hasActiveLitigation = false;

        when(scoringService.calculateScore(context)).thenReturn(score);
        when(litigationService.checkActiveLitigation(context)).thenReturn(hasActiveLitigation);

        // Act: Execute orchestration decision
        DecisionOutcome outcome = decisionOrchestrationService.evaluate(context);

        // Assert: Verify auto-merge is triggered
        assertEquals(DecisionOutcome.AUTO_MERGE, outcome, "Expected AUTO_MERGE outcome for score >= 0.9 with no litigation");
        
        // Verify merge service interaction
        verify(mergeService, times(1)).initiateAutoMerge(eq(context));
        
        // Verify litigation service was checked and no legal escalation occurred
        verify(litigationService, times(1)).checkActiveLitigation(context);
        verify(litigationService, never()).escalateToLegal(any(DecisionContext.class));
    }
}
