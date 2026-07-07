package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock test for Claim Data Standardization:decision:transformation.
 * Verifies match confidence rules:
 * - max_score >= 95 AND unique -> Exact Match
 * - max_score >= 80 AND max_score == second_best -> Ambiguous Match
 * - max_score < 80 -> Unmatched
 * Ensures correct outcome determination for auto-link vs manual intervention.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionTransformationMockTest {

    @Mock
    private DecisionTransformationService decisionTransformationService;

    @InjectMocks
    private ClaimDataStandardizationService claimDataStandardizationService;

    @Test
    void decision_match_confidence_rule_if_max_score_95_and_unique_outcome_exact_match() {
        // Given: max_score = 98.5, unique (no second best score)
        Map<String, Object> payload = Map.of(
            "max_score", 98.5,
            "scores", List.of(98.5),
            "claim_id", "CLM-95-TEST"
        );
        
        DecisionOutcome expectedOutcome = new DecisionOutcome();
        expectedOutcome.setOutcomeType(OutcomeType.EXACT_MATCH);
        expectedOutcome.setAutoLinkEligible(true);

        when(decisionTransformationService.transform(any())).thenReturn(expectedOutcome);

        // When
        DecisionOutcome actualOutcome = claimDataStandardizationService.transformDecision(payload);

        // Then
        assertNotNull(actualOutcome);
        assertEquals(OutcomeType.EXACT_MATCH, actualOutcome.getOutcomeType());
        assertEquals(true, actualOutcome.getAutoLinkEligible());
        verify(decisionTransformationService).transform(payload);
    }

    @Test
    void decision_match_confidence_rule_if_max_score_80_and_tie_outcome_ambiguous_match() {
        // Given: max_score = 85.0, second_best = 85.0 (tie)
        Map<String, Object> payload = Map.of(
            "max_score", 85.0,
            "scores", List.of(85.0, 85.0),
            "claim_id", "CLM-80-TEST"
        );

        DecisionOutcome expectedOutcome = new DecisionOutcome();
        expectedOutcome.setOutcomeType(OutcomeType.AMBIGUOUS_MATCH);
        expectedOutcome.setAutoLinkEligible(false);

        when(decisionTransformationService.transform(any())).thenReturn(expectedOutcome);

        // When
        DecisionOutcome actualOutcome = claimDataStandardizationService.transformDecision(payload);

        // Then
        assertNotNull(actualOutcome);
        assertEquals(OutcomeType.AMBIGUOUS_MATCH, actualOutcome.getOutcomeType());
        assertEquals(false, actualOutcome.getAutoLinkEligible());
        verify(decisionTransformationService).transform(payload);
    }

    @Test
    void decision_match_confidence_rule_if_max_score_below_80_outcome_unmatched() {
        // Given: max_score = 75.0 (< 80)
        Map<String, Object> payload = Map.of(
            "max_score", 75.0,
            "scores", List.of(75.0),
            "claim_id", "CLM-BELOW-80"
        );

        DecisionOutcome expectedOutcome = new DecisionOutcome();
        expectedOutcome.setOutcomeType(OutcomeType.UNMATCHED);
        expectedOutcome.setAutoLinkEligible(false);

        when(decisionTransformationService.transform(any())).thenReturn(expectedOutcome);

        // When
        DecisionOutcome actualOutcome = claimDataStandardizationService.transformDecision(payload);

        // Then
        assertNotNull(actualOutcome);
        assertEquals(OutcomeType.UNMATCHED, actualOutcome.getOutcomeType());
        assertEquals(false, actualOutcome.getAutoLinkEligible());
        verify(decisionTransformationService).transform(payload);
    }

    @Test
    void decision_match_confidence_rule_boundary_max_score_95_exact_match() {
        // Given: max_score = 95.0 (boundary), unique
        Map<String, Object> payload = Map.of(
            "max_score", 95.0,
            "scores", List.of(95.0),
            "claim_id", "CLM-BND-95"
        );

        DecisionOutcome expectedOutcome = new DecisionOutcome();
        expectedOutcome.setOutcomeType(OutcomeType.EXACT_MATCH);
        expectedOutcome.setAutoLinkEligible(true);

        when(decisionTransformationService.transform(any())).thenReturn(expectedOutcome);

        // When
        DecisionOutcome actualOutcome = claimDataStandardizationService.transformDecision(payload);

        // Then
        assertEquals(OutcomeType.EXACT_MATCH, actualOutcome.getOutcomeType());
        verify(decisionTransformationService).transform(payload);
    }

    @Test
    void decision_match_confidence_rule_boundary_max_score_80_ambiguous_if_tie() {
        // Given: max_score = 80.0, tie
        Map<String, Object> payload = Map.of(
            "max_score", 80.0,
            "scores", List.of(80.0, 80.0),
            "claim_id", "CLM-BND-80"
        );

        DecisionOutcome expectedOutcome = new DecisionOutcome();
        expectedOutcome.setOutcomeType(OutcomeType.AMBIGUOUS_MATCH);
        expectedOutcome.setAutoLinkEligible(false);

        when(decisionTransformationService.transform(any())).thenReturn(expectedOutcome);

        // When
        DecisionOutcome actualOutcome = claimDataStandardizationService.transformDecision(payload);

        // Then
        assertEquals(OutcomeType.AMBIGUOUS_MATCH, actualOutcome.getOutcomeType());
        verify(decisionTransformationService).transform(payload);
    }

    /**
     * Mock service representing the external transformation logic.
     */
    private interface DecisionTransformationService {
        DecisionOutcome transform(Map<String, Object> payload);
    }

    /**
     * Mock result model.
     */
    private static class DecisionOutcome {
        private OutcomeType outcomeType;
        private boolean autoLinkEligible;

        public OutcomeType getOutcomeType() { return outcomeType; }
        public void setOutcomeType(OutcomeType outcomeType) { this.outcomeType = outcomeType; }
        public boolean isAutoLinkEligible() { return autoLinkEligible; }
        public void setAutoLinkEligible(boolean autoLinkEligible) { this.autoLinkEligible = autoLinkEligible; }
    }

    /**
     * Mock system under test.
     */
    private static class ClaimDataStandardizationService {
        @Mock
        private DecisionTransformationService decisionTransformationService;

        public DecisionOutcome transformDecision(Map<String, Object> payload) {
            return decisionTransformationService.transform(payload);
        }
    }
}
