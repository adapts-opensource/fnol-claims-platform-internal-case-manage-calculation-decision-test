package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration mock tests for Insured Engagement & Tracking state transitions.
 * Verifies decision logic for moderate similarity rules triggering manual review flags.
 */
@ExtendWith(MockitoExtension.class)
class DecisionModerateSimilarityRulePartialMatchOnAddressInsuredTest {

    @Mock
    private SimilarityAnalysisService similarityAnalysisService;

    @Mock
    private StateTransitionEngine stateTransitionEngine;

    @Mock
    private InsuredEngagementContextFactory contextFactory;

    private DecisionModerateSimilarityRulePartialMatchOnAddressInsuredSut decisionSut;

    @BeforeEach
    void setUp() {
        decisionSut = new DecisionModerateSimilarityRulePartialMatchOnAddressInsuredSut(
                similarityAnalysisService,
                stateTransitionEngine,
                contextFactory
        );
    }

    /**
     * Test Case: DecisionModerateSimilarityRulePartialMatchOnAddress
     * Description: {'decision': 'Moderate similarity', 'rule': 'Partial match on address/insured', 'expected_outcome': 'Flag for manual review'}
     */
    @Test
    void decision_moderate_similarity_rule_partial_match_on_address_insured_expected_outcome_flag_for_manual_review() {
        // Given: Insured data with partial address match resulting in moderate similarity
        String insuredId = "INS-98765";
        String claimId = "CLM-12345";
        InsuredRecord inputRecord = new InsuredRecord(insuredId, claimId, "123 Main St", "Apt 4B");
        
        SimilarityAnalysisResult analysisResult = new SimilarityAnalysisResult(
                SimilarityLevel.MODERATE,
                RuleType.PARTIAL_MATCH_ADDRESS,
                Optional.of("Address partial match detected: 123 Main St vs 123 Main St, Apt 4B")
        );

        when(similarityAnalysisService.analyzeInsuredMatch(any(InsuredRecord.class)))
                .thenReturn(analysisResult);

        DecisionContext expectedContext = new DecisionContext(
                insuredId,
                claimId,
                DecisionOutcome.FLAG_FOR_MANUAL_REVIEW,
                "Moderate similarity detected via partial address match"
        );

        // When: System processes the insured engagement decision
        DecisionOutcome actualOutcome = decisionSut.evaluateAndTransition(inputRecord);

        // Then: State transitions to FLAG_FOR_MANUAL_REVIEW
        assertEquals(DecisionOutcome.FLAG_FOR_MANUAL_REVIEW, actualOutcome);

        // Verify interactions
        verify(similarityAnalysisService).analyzeInsuredMatch(inputRecord);
        verify(stateTransitionEngine).transitionToState(eq(claimId), eq(DecisionState.FLAG_FOR_MANUAL_REVIEW));
        verify(contextFactory).createContext(eq(insuredId), eq(claimId), eq(DecisionOutcome.FLAG_FOR_MANUAL_REVIEW));
    }

    // --- Mock DTOs and Enums for compilation context ---

    enum SimilarityLevel {
        HIGH, MODERATE, LOW
    }

    enum RuleType {
        EXACT_MATCH, PARTIAL_MATCH_ADDRESS, NAME_VARIANCE, NONE
    }

    enum DecisionState {
        PENDING, FLAG_FOR_MANUAL_REVIEW, AUTO_APPROVED, REJECTED
    }

    enum DecisionOutcome {
        FLAG_FOR_MANUAL_REVIEW, CONTINUE_AUTO_PROCESSING
    }

    static class InsuredRecord {
        final String insuredId;
        final String claimId;
        final String addressLine1;
        final String addressLine2;

        InsuredRecord(String insuredId, String claimId, String addressLine1, String addressLine2) {
            this.insuredId = insuredId;
            this.claimId = claimId;
            this.addressLine1 = addressLine1;
            this.addressLine2 = addressLine2;
        }
    }

    static class SimilarityAnalysisResult {
        final SimilarityLevel level;
        final RuleType triggeredRule;
        final Optional<String> detail;

        SimilarityAnalysisResult(SimilarityLevel level, RuleType triggeredRule, Optional<String> detail) {
            this.level = level;
            this.triggeredRule = triggeredRule;
            this.detail = detail;
        }
    }

    static class DecisionContext {
        final String insuredId;
        final String claimId;
        final DecisionOutcome outcome;
        final String rationale;

        DecisionContext(String insuredId, String claimId, DecisionOutcome outcome, String rationale) {
            this.insuredId = insuredId;
            this.claimId = claimId;
            this.outcome = outcome;
            this.rationale = rationale;
        }
    }

    // --- Production Sut Stub (Mocked Dependencies Injected) ---
    static class DecisionModerateSimilarityRulePartialMatchOnAddressInsuredSut {
        private final SimilarityAnalysisService similarityAnalysisService;
        private final StateTransitionEngine stateTransitionEngine;
        private final InsuredEngagementContextFactory contextFactory;

        DecisionModerateSimilarityRulePartialMatchOnAddressInsuredSut(
                SimilarityAnalysisService similarityAnalysisService,
                StateTransitionEngine stateTransitionEngine,
                InsuredEngagementContextFactory contextFactory) {
            this.similarityAnalysisService = similarityAnalysisService;
            this.stateTransitionEngine = stateTransitionEngine;
            this.contextFactory = contextFactory;
        }

        DecisionOutcome evaluateAndTransition(InsuredRecord record) {
            SimilarityAnalysisResult analysis = similarityAnalysisService.analyzeInsuredMatch(record);
            
            if (analysis.level == SimilarityLevel.MODERATE && analysis.triggeredRule == RuleType.PARTIAL_MATCH_ADDRESS) {
                stateTransitionEngine.transitionToState(record.claimId, DecisionState.FLAG_FOR_MANUAL_REVIEW);
                contextFactory.createContext(record.insuredId, record.claimId, DecisionOutcome.FLAG_FOR_MANUAL_REVIEW);
                return DecisionOutcome.FLAG_FOR_MANUAL_REVIEW;
            }
            
            throw new IllegalStateException("Unexpected state for moderate similarity rule");
        }
    }

    interface SimilarityAnalysisService {
        SimilarityAnalysisResult analyzeInsuredMatch(InsuredRecord record);
    }

    interface StateTransitionEngine {
        void transitionToState(String claimId, DecisionState newState);
    }

    interface InsuredEngagementContextFactory {
        DecisionContext createContext(String insuredId, String claimId, DecisionOutcome outcome);
    }
}
