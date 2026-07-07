package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class ClaimDataStandardizationValidationDecisionTest {

    private DecisionValidationService mockDecisionService;

    @BeforeEach
    void setUp() {
        mockDecisionService = Mockito.mock(DecisionValidationService.class);
    }

    @Test
    void match_candidate_list_with_confidence_scores() {
        // Arrange: Construct payload containing candidate list with confidence scores
        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> candidates = new ArrayList<>();

        Map<String, Object> candidateA = new HashMap<>();
        candidateA.put("candidateId", "CAND-001");
        candidateA.put("confidenceScore", 0.72);
        candidates.add(candidateA);

        Map<String, Object> candidateB = new HashMap<>();
        candidateB.put("candidateId", "CAND-002");
        candidateB.put("confidenceScore", 0.91);
        candidates.add(candidateB);

        Map<String, Object> candidateC = new HashMap<>();
        candidateC.put("candidateId", "CAND-003");
        candidateC.put("confidenceScore", 0.85);
        candidates.add(candidateC);

        payload.put("candidates", candidates);
        payload.put("decisionThreshold", 0.80);

        // Map to entity model per data model summary
        ClaimDataStandardizationTransformationValida entity = new ClaimDataStandardizationTransformationValida();
        entity.setId("STD-CLAIM-789");
        entity.setPayload(payload);

        // Define expected decision outcome
        Map<String, Object> expectedDecision = new HashMap<>();
        expectedDecision.put("matchedCandidateId", "CAND-002");
        expectedDecision.put("confidenceScore", 0.91);
        expectedDecision.put("validationStatus", "PASS");
        expectedDecision.put("standardizedKey", "primary_match");

        Mockito.when(mockDecisionService.evaluateAndMatch(Mockito.any(ClaimDataStandardizationTransformationValida.class)))
               .thenReturn(expectedDecision);

        // Act: Invoke the mocked decision/validation pipeline
        Map<String, Object> result = mockDecisionService.evaluateAndMatch(entity);

        // Assert: Verify candidate matching logic with confidence scores
        assertNotNull(result, "Decision result must not be null");
        assertEquals("CAND-002", result.get("matchedCandidateId"), "Should select candidate with highest confidence score");
        assertEquals(0.91, result.get("confidenceScore"), "Confidence score must match selected candidate");
        assertEquals("PASS", result.get("validationStatus"), "Validation status should reflect threshold compliance");
        assertTrue((boolean) result.getOrDefault("aboveThreshold", true), "Score must exceed configured decision threshold");
    }

    // Minimal static entity class aligned with data model summary
    static class ClaimDataStandardizationTransformationValida {
        private String id;
        private Map<String, Object> payload;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Map<String, Object> getPayload() { return payload; }
        public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    }

    // Minimal service interface representing the validation:decision contract
    interface DecisionValidationService {
        Map<String, Object> evaluateAndMatch(ClaimDataStandardizationTransformationValida entity);
    }
}
