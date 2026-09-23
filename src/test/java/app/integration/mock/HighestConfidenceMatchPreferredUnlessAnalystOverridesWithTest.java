package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization: validation: decision logic.
 * Verifies decision selection based on confidence scores and analyst overrides.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionMockTest {

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private DocumentStoreService documentStoreService;

    @InjectMocks
    private ClaimStandardizationDecisionService decisionService;

    private static final String CLAIM_ID = "claim-std-001";
    private static final String ANALYST_CHOICE_ID = "match-analyst-choice";
    private static final String HIGHEST_CONFIDENCE_ID = "match-highest-conf";
    private static final String LOW_CONFIDENCE_ID = "match-low-conf";
    private static final String VALID_JUSTIFICATION = "Customer provided new policy number verified via portal.";
    private static final Map<String, Object> EMPTY_PAYLOAD = Map.of("id", CLAIM_ID, "matches", List.of());

    @BeforeEach
    void setUp() {
        // Reset mocks between tests to ensure isolation
        reset(rulesEngineService, documentStoreService);
    }

    @Test
    void highest_confidence_match_preferred_unless_analyst_overrides_with_justification() {
        // Arrange: Analyst overrides with valid justification
        // Expected: Analyst choice should be selected despite lower confidence
        Map<String, Object> payload = createPayloadWithAnalystOverride(
            CLAIM_ID,
            ANALYST_CHOICE_ID,
            VALID_JUSTIFICATION,
            List.of(
                createMatch(ANALYST_CHOICE_ID, 0.85),
                createMatch(HIGHEST_CONFIDENCE_ID, 0.95)
            )
        );

        when(rulesEngineService.getMatches(anyString())).thenReturn((List<Map<String, Object>>) payload.get("matches"));

        // Act
        Map<String, Object> decision = decisionService.decide(CLAIM_ID, payload);

        // Assert
        assertEquals(ANALYST_CHOICE_ID, decision.get("selected_match_id"),
            "Analyst override with justification should prevail over highest confidence match.");
        assertEquals("OVERRIDDEN", decision.get("decision_status"),
            "Decision status should reflect analyst override.");
        verify(rulesEngineService, times(1)).getMatches(CLAIM_ID);
    }

    @Test
    void highest_confidence_match_selected_when_no_analyst_override() {
        // Arrange: No analyst override present
        // Expected: Highest confidence match should be selected
        Map<String, Object> payload = createPayloadNoOverride(
            CLAIM_ID,
            List.of(
                createMatch(LOW_CONFIDENCE_ID, 0.60),
                createMatch(HIGHEST_CONFIDENCE_ID, 0.98)
            )
        );

        when(rulesEngineService.getMatches(anyString())).thenReturn((List<Map<String, Object>>) payload.get("matches"));

        // Act
        Map<String, Object> decision = decisionService.decide(CLAIM_ID, payload);

        // Assert
        assertEquals(HIGHEST_CONFIDENCE_ID, decision.get("selected_match_id"),
            "Highest confidence match should be selected when no analyst override exists.");
        assertEquals("AUTO_SELECTED", decision.get("decision_status"),
            "Decision status should indicate automatic selection.");
        verify(rulesEngineService, times(1)).getMatches(CLAIM_ID);
    }

    @Test
    void analyst_override_rejected_without_justification() {
        // Arrange: Analyst override present but justification is missing/null
        // Expected: Fallback to highest confidence match (override invalid)
        Map<String, Object> payload = createPayloadWithAnalystOverride(
            CLAIM_ID,
            ANALYST_CHOICE_ID,
            null, // Justification missing
            List.of(
                createMatch(ANALYST_CHOICE_ID, 0.50),
                createMatch(HIGHEST_CONFIDENCE_ID, 0.90)
            )
        );

        when(rulesEngineService.getMatches(anyString())).thenReturn((List<Map<String, Object>>) payload.get("matches"));

        // Act
        Map<String, Object> decision = decisionService.decide(CLAIM_ID, payload);

        // Assert
        assertEquals(HIGHEST_CONFIDENCE_ID, decision.get("selected_match_id"),
            "Analyst override without justification must be rejected; highest confidence should win.");
        assertEquals("AUTO_SELECTED", decision.get("decision_status"),
            "Decision status should fall back to auto-selection when override is invalid.");
        verify(rulesEngineService, times(1)).getMatches(CLAIM_ID);
    }

    @Test
    void empty_matches_list_falls_back_to_default_handling() {
        // Arrange: No matches found
        when(rulesEngineService.getMatches(anyString())).thenReturn(List.of());

        // Act
        Map<String, Object> decision = decisionService.decide(CLAIM_ID, EMPTY_PAYLOAD);

        // Assert
        assertNull(decision.get("selected_match_id"),
            "No match should be selected when matches list is empty.");
        assertEquals("NO_MATCHES", decision.get("decision_status"),
            "Status should indicate no matches available.");
    }

    // Helper methods to construct test data payloads
    private Map<String, Object> createPayloadWithAnalystOverride(String claimId, String analystChoice, String justification, List<Map<String, Object>> matches) {
        return Map.of(
            "id", claimId,
            "analyst_decision", analystChoice,
            "justification", justification,
            "matches", matches
        );
    }

    private Map<String, Object> createPayloadNoOverride(String claimId, List<Map<String, Object>> matches) {
        return Map.of(
            "id", claimId,
            "matches", matches
        );
    }

    private Map<String, Object> createMatch(String matchId, double confidence) {
        return Map.of(
            "id", matchId,
            "confidence", confidence
        );
    }
}
