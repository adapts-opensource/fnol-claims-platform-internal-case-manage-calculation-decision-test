package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDecisionEnrichmentTest {

    @Mock
    private DecisionRuleEngine decisionRuleEngine;

    @Mock
    private CoverageContextResolver coverageContextResolver;

    @InjectMocks
    private ClaimDataEnrichmentProcessor enrichmentProcessor;

    @BeforeEach
    void setUp() {
        // Mock initialization handled by MockitoExtension
    }

    @Test
    void decision_match_found_rule_single_match_with_valid_period_and_no_moratorium_expected_outcome_proceed_to_claim_creation_with_coverage_context() {
        // Arrange
        String claimId = "CLM-STD-001";
        Map<String, Object> payload = Map.of(
            "policyId", "POL-AUTO-456",
            "incidentDate", "2023-10-01",
            "claimType", "FIRE"
        );

        DecisionRuleMatch match = new DecisionRuleMatch("RULE-001", true, true, false);
        CoverageContext context = new CoverageContext("AUTO", "COMPREHENSIVE", 100000.0);

        when(decisionRuleEngine.evaluate(payload)).thenReturn(match);
        when(coverageContextResolver.resolve(payload)).thenReturn(context);

        // Act
        EnrichmentResult result = enrichmentProcessor.processEnrichment(claimId, payload);

        // Assert
        assertNotNull(result);
        assertEquals(EnrichmentStatus.PROCEED_TO_CLAIM_CREATION, result.getStatus());
        assertNotNull(result.getCoverageContext());
        assertEquals(context, result.getCoverageContext());
        assertFalse(result.isMoratoriumApplied());
        assertTrue(result.isSingleMatchFound());
        
        verify(decisionRuleEngine).evaluate(payload);
        verify(coverageContextResolver).resolve(payload);
    }

    // Supporting types for mock isolation
    static class DecisionRuleMatch {
        final String ruleId; final boolean singleMatch; final boolean validPeriod; final boolean noMoratorium;
        DecisionRuleMatch(String ruleId, boolean singleMatch, boolean validPeriod, boolean noMoratorium) {
            this.ruleId = ruleId; this.singleMatch = singleMatch; this.validPeriod = validPeriod; this.noMoratorium = noMoratorium;
        }
        public boolean isSingleMatch() { return singleMatch; }
        public boolean isValidPeriod() { return validPeriod; }
        public boolean isNoMoratorium() { return noMoratorium; }
    }

    static class CoverageContext {
        final String policyType; final String coverageType; final double limit;
        CoverageContext(String policyType, String coverageType, double limit) {
            this.policyType = policyType; this.coverageType = coverageType; this.limit = limit;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CoverageContext)) return false;
            CoverageContext that = (CoverageContext) o;
            return Double.compare(limit, that.limit) == 0 && policyType.equals(that.policyType) && coverageType.equals(that.coverageType);
        }
    }

    enum EnrichmentStatus { PROCEED_TO_CLAIM_CREATION, REJECT, FLAG_FOR_REVIEW }

    static class EnrichmentResult {
        private final EnrichmentStatus status;
        private final CoverageContext coverageContext;
        private final boolean singleMatchFound;
        private final boolean moratoriumApplied;
        EnrichmentResult(EnrichmentStatus status, CoverageContext coverageContext, boolean singleMatchFound, boolean moratoriumApplied) {
            this.status = status; this.coverageContext = coverageContext; this.singleMatchFound = singleMatchFound; this.moratoriumApplied = moratoriumApplied;
        }
        public EnrichmentStatus getStatus() { return status; }
        public CoverageContext getCoverageContext() { return coverageContext; }
        public boolean isSingleMatchFound() { return singleMatchFound; }
        public boolean isMoratoriumApplied() { return moratoriumApplied; }
    }

    interface DecisionRuleEngine { DecisionRuleMatch evaluate(Map<String, Object> payload); }
    interface CoverageContextResolver { CoverageContext resolve(Map<String, Object> payload); }

    static class ClaimDataEnrichmentProcessor {
        private final DecisionRuleEngine decisionRuleEngine;
        private final CoverageContextResolver coverageContextResolver;

        ClaimDataEnrichmentProcessor(DecisionRuleEngine decisionRuleEngine, CoverageContextResolver coverageContextResolver) {
            this.decisionRuleEngine = decisionRuleEngine;
            this.coverageContextResolver = coverageContextResolver;
        }

        EnrichmentResult processEnrichment(String claimId, Map<String, Object> payload) {
            DecisionRuleMatch match = decisionRuleEngine.evaluate(payload);
            if (match.isSingleMatch() && match.isValidPeriod() && match.isNoMoratorium()) {
                CoverageContext ctx = coverageContextResolver.resolve(payload);
                return new EnrichmentResult(EnrichmentStatus.PROCEED_TO_CLAIM_CREATION, ctx, true, false);
            }
            throw new IllegalStateException("Rule evaluation failed for claim: " + claimId);
        }
    }
}
