package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionStateTransitionCalculationMockTest {

    @Mock
    private PolicyDataQueryService policyDataQueryService;

    @InjectMocks
    private StateTransitionCalculator stateTransitionCalculator;

    @BeforeEach
    void setUp() {
        // Reset mock state before each test to ensure isolation
    }

    @Test
    void description_normalizes_inputs_queries_policy_data_scores_matches_based_on_exact_and_fuzzy_criteria_and_determines_the_best_match_or_flags_for_review() {
        // Given: Raw inputs containing whitespace and inconsistent casing
        Map<String, Object> rawPayload = Map.of(
            "claimant_name", "  JOHN DOE  ",
            "policy_number", "POL-12345",
            "date_of_loss", "2023-10-01"
        );

        // Mock policy data responses representing exact and fuzzy matches
        List<PolicyMatch> mockMatches = List.of(
            new PolicyMatch("POL-12345", 0.98, MatchType.EXACT),
            new PolicyMatch("POL-1234X", 0.72, MatchType.FUZZY)
        );

        when(policyDataQueryService.queryByPolicyNumber(anyString())).thenReturn(mockMatches);

        // When: Trigger state transition calculation
        StateTransitionResult result = stateTransitionCalculator.calculate(rawPayload);

        // Then: Verify normalization, query, scoring, and best match selection
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isMatchFound(), "Should identify a valid match");
        assertEquals("POL-12345", result.getBestMatchPolicyNumber(), "Should select exact match as best");
        assertEquals(0.98, result.getBestMatchScore(), 0.001, "Score should reflect exact match confidence");
        assertFalse(result.isFlaggedForReview(), "Exact match should not trigger manual review");

        verify(policyDataQueryService).queryByPolicyNumber("POL-12345");
        verifyNoMoreInteractions(policyDataQueryService);
    }

    @Test
    void description_normalizes_inputs_queries_policy_data_scores_matches_based_on_exact_and_fuzzy_criteria_and_determines_the_best_match_or_flags_for_review_when_low_fuzzy_score() {
        // Given: Only a low-confidence fuzzy match available
        Map<String, Object> rawPayload = Map.of("policy_number", "POL-98765");
        List<PolicyMatch> mockMatches = List.of(
            new PolicyMatch("POL-9876X", 0.65, MatchType.FUZZY)
        );
        when(policyDataQueryService.queryByPolicyNumber(anyString())).thenReturn(mockMatches);

        // When
        StateTransitionResult result = stateTransitionCalculator.calculate(rawPayload);

        // Then: Should flag for manual review due to low fuzzy score
        assertTrue(result.isFlaggedForReview(), "Low fuzzy score should flag for review");
        assertFalse(result.isMatchFound(), "Should not confirm a match");
        verify(policyDataQueryService).queryByPolicyNumber("POL-98765");
    }
}

// Supporting types for compilation and mock isolation
record PolicyMatch(String policyNumber, double score, MatchType type) {}

record StateTransitionResult(boolean matchFound, String bestMatchPolicyNumber, double bestMatchScore, boolean flaggedForReview) {}

interface PolicyDataQueryService {
    List<PolicyMatch> queryByPolicyNumber(String policyNumber);
}

class StateTransitionCalculator {
    private PolicyDataQueryService policyDataQueryService;

    void setPolicyDataQueryService(PolicyDataQueryService policyDataQueryService) {
        this.policyDataQueryService = policyDataQueryService;
    }

    StateTransitionResult calculate(Map<String, Object> payload) {
        String normalizedPolicyNumber = normalizeString((String) payload.getOrDefault("policy_number", ""));
        List<PolicyMatch> matches = policyDataQueryService.queryByPolicyNumber(normalizedPolicyNumber);

        if (matches.isEmpty()) {
            return new StateTransitionResult(false, null, 0.0, true);
        }

        PolicyMatch best = matches.stream()
            .max((a, b) -> Double.compare(a.score(), b.score()))
            .orElse(null);

        boolean isExactMatch = best.type() == MatchType.EXACT;
        boolean isHighConfidence = best.score() >= 0.80;

        if (isExactMatch || isHighConfidence) {
            return new StateTransitionResult(true, best.policyNumber(), best.score(), false);
        }

        return new StateTransitionResult(false, null, best.score(), true);
    }

    private String normalizeString(String input) {
        return input == null ? "" : input.trim().toUpperCase();
    }
}
