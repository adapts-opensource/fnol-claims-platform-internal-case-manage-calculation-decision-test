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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionHighSimilarityScoreRuleSamePolicyAddressTest {

    @Mock
    private SimilarityScoringService similarityScoringService;

    @Mock
    private StateTransitionService stateTransitionService;

    @Mock
    private DuplicateFlaggingService duplicateFlaggingService;

    @Mock
    private CommunicationService communicationService; // SES mock

    @Mock
    private DataPersistenceService dataPersistenceService; // DynamoDB mock

    @Mock
    private DocumentStoreService documentStoreService; // S3 mock

    @InjectMocks
    private InsuredEngagementDecisionEngine decisionEngine;

    private ClaimContext newClaim;
    private ClaimContext referenceClaim;

    @BeforeEach
    void setUp() {
        newClaim = new ClaimContext();
        newClaim.setPolicyNumber("POL-12345");
        newClaim.setAddress("123 Main St, Springfield, IL 62704");
        newClaim.setDateOfLoss("2023-10-01");
        newClaim.setCause("Fire");
        newClaim.setClaimId("CLM-NEW-001");

        referenceClaim = new ClaimContext();
        referenceClaim.setPolicyNumber("POL-12345");
        referenceClaim.setAddress("123 Main St, Springfield, IL 62704");
        referenceClaim.setDateOfLoss("2023-10-01");
        referenceClaim.setCause("Fire");
        referenceClaim.setClaimId("CLM-REF-002");
    }

    @Test
    void decision_high_similarity_score_rule_same_policy_address_dol_cause_expected_outcome_flag_duplicate_suggest_merge_reopen_supplemental() {
        // Arrange: Mock high similarity score for same policy, address, DOL, cause
        when(similarityScoringService.calculateSimilarityScore(any(ClaimContext.class), any(ClaimContext.class)))
                .thenReturn(0.98);

        // Act: Trigger decision engine state transition
        DecisionOutcome outcome = decisionEngine.evaluateAndTransition(newClaim, referenceClaim);

        // Assert: Verify expected outcome flags duplicate and suggests actions
        assertNotNull(outcome, "Decision outcome should not be null");
        assertEquals(DecisionOutcome.DecisionState.DUPLICATE_FLAGGED, outcome.getState());
        assertTrue(outcome.isDuplicateFlag(), "Outcome should flag duplicate");
        assertEquals(List.of(
                DecisionOutcome.Suggestion.MERGE,
                DecisionOutcome.Suggestion.REOPEN,
                DecisionOutcome.Suggestion.SUPPLEMENTAL
        ), outcome.getSuggestions());

        // Verify: Ensure state transition and mocked services were invoked correctly
        verify(similarityScoringService, times(1)).calculateSimilarityScore(newClaim, referenceClaim);
        verify(stateTransitionService, times(1)).transitionState(eq(newClaim.getClaimId()), eq("DUPLICATE_REVIEW"));
        verify(duplicateFlaggingService, times(1)).flagDuplicate(eq(newClaim.getClaimId()), anyString());
        
        // Verify infra contracts are mocked and not called live
        verifyNoInteractions(communicationService, dataPersistenceService, documentStoreService);
    }

    // Supporting domain interfaces/classes for compilation context
    interface SimilarityScoringService {
        double calculateSimilarityScore(ClaimContext claim1, ClaimContext claim2);
    }

    interface StateTransitionService {
        void transitionState(String claimId, String newState);
    }

    interface DuplicateFlaggingService {
        void flagDuplicate(String claimId, String reason);
    }

    interface CommunicationService {
        String sendEmail(String from, List<String> to, String region);
    }

    interface DataPersistenceService {
        Map<String, Object> saveItem(String tableName, Map<String, Object> payload);
    }

    interface DocumentStoreService {
        String uploadDocument(String bucket, String keyPattern, byte[] content);
    }

    static class ClaimContext {
        private String policyNumber;
        private String address;
        private String dateOfLoss;
        private String cause;
        private String claimId;
        public void setPolicyNumber(String p) { this.policyNumber = p; }
        public String getPolicyNumber() { return policyNumber; }
        public void setAddress(String a) { this.address = a; }
        public String getAddress() { return address; }
        public void setDateOfLoss(String d) { this.dateOfLoss = d; }
        public String getDateOfLoss() { return dateOfLoss; }
        public void setCause(String c) { this.cause = c; }
        public String getCause() { return cause; }
        public void setClaimId(String id) { this.claimId = id; }
        public String getClaimId() { return claimId; }
    }

    static class DecisionOutcome {
        public enum DecisionState { DUPLICATE_FLAGGED, UNDER_REVIEW, APPROVED }
        public enum Suggestion { MERGE, REOPEN, SUPPLEMENTAL }
        private DecisionState state;
        private boolean duplicateFlag;
        private List<Suggestion> suggestions;
        public DecisionState getState() { return state; }
        public void setState(DecisionState state) { this.state = state; }
        public boolean isDuplicateFlag() { return duplicateFlag; }
        public void setDuplicateFlag(boolean duplicateFlag) { this.duplicateFlag = duplicateFlag; }
        public List<Suggestion> getSuggestions() { return suggestions; }
        public void setSuggestions(List<Suggestion> suggestions) { this.suggestions = suggestions; }
    }

    static class InsuredEngagementDecisionEngine {
        private final SimilarityScoringService similarityScoringService;
        private final StateTransitionService stateTransitionService;
        private final DuplicateFlaggingService duplicateFlaggingService;

        public InsuredEngagementDecisionEngine(SimilarityScoringService s, StateTransitionService t, DuplicateFlaggingService d) {
            this.similarityScoringService = s;
            this.stateTransitionService = t;
            this.duplicateFlaggingService = d;
        }

        public DecisionOutcome evaluateAndTransition(ClaimContext newClaim, ClaimContext refClaim) {
            double score = similarityScoringService.calculateSimilarityScore(newClaim, refClaim);
            DecisionOutcome outcome = new DecisionOutcome();
            if (score > 0.9) {
                outcome.setState(DecisionOutcome.DecisionState.DUPLICATE_FLAGGED);
                outcome.setDuplicateFlag(true);
                outcome.setSuggestions(List.of(DecisionOutcome.Suggestion.MERGE, DecisionOutcome.Suggestion.REOPEN, DecisionOutcome.Suggestion.SUPPLEMENTAL));
                stateTransitionService.transitionState(newClaim.getClaimId(), "DUPLICATE_REVIEW");
                duplicateFlaggingService.flagDuplicate(newClaim.getClaimId(), "High similarity score");
            }
            return outcome;
        }
    }
}
