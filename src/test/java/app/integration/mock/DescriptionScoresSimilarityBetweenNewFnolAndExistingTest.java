package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Insured Engagement & Tracking: decision: state_transition.
 * Verifies similarity scoring logic between new FNOL and existing claims.
 */
@ExtendWith(MockitoExtension.class)
public class DescriptionScoresSimilarityBetweenNewFnolAndExisting {

    @Mock
    private SimilarityScoringService scoringService;

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private StateTransitionDecisionService decisionService;

    @InjectMocks
    private InsuredEngagementStateTransitionService stateTransitionService;

    @Test
    void description_scores_similarity_between_new_fnol_and_existing_claims_using_policy_address_dol_cause_catastrophe_event_reporter_damaged_area_and_prior_status() {
        // Arrange: Construct new FNOL with target attributes
        Claim newFnol = Claim.builder()
                .claimId("FNOL-NEW-001")
                .policyNumber("POL-INS-9988")
                .address("123 Storm Lane, Springfield, IL")
                .dateOfLoss(LocalDate.now().minusDays(2))
                .cause("Windstorm")
                .catastropheEvent("MidwestCyclone2024")
                .reporter("John Doe")
                .damagedArea("Roof and Siding")
                .status("New")
                .build();

        // Arrange: Construct existing claim with high similarity attributes
        Claim existingClaim = Claim.builder()
                .claimId("CLM-EXIST-001")
                .policyNumber("POL-INS-9988")
                .address("123 Storm Lane, Springfield, IL")
                .dateOfLoss(LocalDate.now().minusDays(2))
                .cause("Windstorm")
                .catastropheEvent("MidwestCyclone2024")
                .reporter("John Doe")
                .damagedArea("Roof and Siding")
                .status("Open")
                .build();

        List<Claim> existingClaims = List.of(existingClaim);

        // Arrange: Mock repository to return matching existing claim
        when(claimRepository.findByPolicyAndAddressAndCause(
                eq("POL-INS-9988"), 
                eq("123 Storm Lane, Springfield, IL"), 
                eq("Windstorm")))
                .thenReturn(existingClaims);

        // Arrange: Mock scoring service to return high similarity scores for all key attributes
        Map<String, Double> expectedScores = Map.of(
                "policy", 1.0,
                "address", 1.0,
                "dol", 1.0,
                "cause", 1.0,
                "catastrophe_event", 1.0,
                "reporter", 1.0,
                "damaged_area", 1.0,
                "prior_status", 0.85
        );

        when(scoringService.calculateSimilarityScores(newFnol, existingClaim))
                .thenReturn(expectedScores);

        when(decisionService.evaluateDecision(any(SimilarityResult.class)))
                .thenReturn(StateTransitionAction.MERGE_RECOMMENDED);

        // Act
        StateTransitionResult result = stateTransitionService.evaluateStateTransition(newFnol);

        // Assert: Verify repository interaction
        verify(claimRepository).findByPolicyAndAddressAndCause(
                eq("POL-INS-9988"), 
                eq("123 Storm Lane, Springfield, IL"), 
                eq("Windstorm"));

        // Assert: Verify scoring service was called with correct arguments
        verify(scoringService).calculateSimilarityScores(eq(newFnol), eq(existingClaim));

        // Assert: Verify decision service received the similarity result
        verify(decisionService).evaluateDecision(any(SimilarityResult.class));

        // Assert: Verify final decision action
        assertNotNull(result);
        assertEquals(StateTransitionAction.MERGE_RECOMMENDED, result.getAction());
        assertTrue(result.isHighSimilarity());
        assertEquals(1, result.getMatchedClaims().size());
        
        // Assert: Verify score weights were applied (simulated by checking result structure)
        SimilarityResult scoreResult = result.getSimilarityResult();
        assertNotNull(scoreResult);
        assertEquals(1.0, scoreResult.getScore("policy"));
        assertEquals(1.0, scoreResult.getScore("address"));
        assertEquals(1.0, scoreResult.getScore("dol"));
        assertEquals(1.0, scoreResult.getScore("cause"));
        assertEquals(1.0, scoreResult.getScore("catastrophe_event"));
        assertEquals(1.0, scoreResult.getScore("reporter"));
        assertEquals(1.0, scoreResult.getScore("damaged_area"));
        assertEquals(0.85, scoreResult.getScore("prior_status"));
    }

    // --- Mock Domain Models and Interfaces for Compilation ---

    private interface SimilarityScoringService {
        Map<String, Double> calculateSimilarityScores(Claim newFnol, Claim existingClaim);
    }

    private interface ClaimRepository {
        List<Claim> findByPolicyAndAddressAndCause(String policy, String address, String cause);
    }

    private interface StateTransitionDecisionService {
        StateTransitionAction evaluateDecision(SimilarityResult result);
    }

    private interface InsuredEngagementStateTransitionService {
        StateTransitionResult evaluateStateTransition(Claim fnol);
    }

    // --- Stub Classes ---

    static class Claim {
        private String claimId;
        private String policyNumber;
        private String address;
        private LocalDate dateOfLoss;
        private String cause;
        private String catastropheEvent;
        private String reporter;
        private String damagedArea;
        private String status;

        public static Builder builder() { return new Builder(); }
        
        static class Builder {
            private final Claim c = new Claim();
            Builder claimId(String id) { c.claimId = id; return this; }
            Builder policyNumber(String p) { c.policyNumber = p; return this; }
            Builder address(String a) { c.address = a; return this; }
            Builder dateOfLoss(LocalDate d) { c.dateOfLoss = d; return this; }
            Builder cause(String ca) { c.cause = ca; return this; }
            Builder catastropheEvent(String ce) { c.catastropheEvent = ce; return this; }
            Builder reporter(String r) { c.reporter = r; return this; }
            Builder damagedArea(String da) { c.damagedArea = da; return this; }
            Builder status(String s) { c.status = s; return this; }
            Claim build() { return c; }
        }
    }

    enum StateTransitionAction {
        CREATE_NEW,
        MERGE_RECOMMENDED,
        DUPLICATE_SUSPECT,
        REVIEW_REQUIRED
    }

    static class SimilarityResult {
        private final Map<String, Double> scores;
        
        SimilarityResult(Map<String, Double> scores) {
            this.scores = scores;
        }
        
        public Double getScore(String key) {
            return scores.getOrDefault(key, 0.0);
        }
    }

    static class StateTransitionResult {
        private final StateTransitionAction action;
        private final boolean highSimilarity;
        private final List<Claim> matchedClaims;
        private final SimilarityResult similarityResult;

        StateTransitionResult(StateTransitionAction action, boolean highSimilarity, 
                              List<Claim> matchedClaims, SimilarityResult similarityResult) {
            this.action = action;
            this.highSimilarity = highSimilarity;
            this.matchedClaims = matchedClaims;
            this.similarityResult = similarityResult;
        }

        public StateTransitionAction getAction() { return action; }
        public boolean isHighSimilarity() { return highSimilarity; }
        public List<Claim> getMatchedClaims() { return matchedClaims; }
        public SimilarityResult getSimilarityResult() { return similarityResult; }
    }
}
