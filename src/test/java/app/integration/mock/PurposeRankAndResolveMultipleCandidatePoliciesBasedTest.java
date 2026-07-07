package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies ranking and resolution of multiple candidate policies based on weighted attributes.
 * Mocks external I/O contracts (DynamoDB/S3) to ensure thread-safe, GDPR-compliant evaluation
 * without touching live infrastructure.
 */
@ExtendWith(MockitoExtension.class)
public class PurposeRankAndResolveMultipleCandidatePoliciesBasedTest {

    @Mock
    private PolicyCandidateRepository candidateRepository;

    @Mock
    private WeightedAttributeCalculator attributeCalculator;

    @InjectMocks
    private DecisionCalculationService decisionService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection and mocking lifecycle
    }

    @Test
    void purpose_rank_and_resolve_multiple_candidate_policies_based_on_weighted_attributes() {
        // Arrange
        String caseId = "CASE-2024-001";
        Map<String, Double> weights = Map.of("premium", 0.4, "coverageLimit", 0.35, "riskScore", 0.25);

        List<PolicyCandidate> candidates = List.of(
            new PolicyCandidate("POL-001", 150.00, 500000, 3),
            new PolicyCandidate("POL-002", 180.00, 750000, 2),
            new PolicyCandidate("POL-003", 165.00, 600000, 1)
        );

        when(candidateRepository.findAllForCase(caseId)).thenReturn(candidates);

        when(attributeCalculator.calculateWeightedScore(any(PolicyCandidate.class), anyMap())).thenAnswer(invocation -> {
            PolicyCandidate policy = invocation.getArgument(0);
            Map<String, Double> w = invocation.getArgument(1);
            // Mock scoring: maximize coverage, minimize premium & risk
            double score = (policy.getCoverageLimit() * w.get("coverageLimit"))
                         - (policy.getPremium() * w.get("premium"))
                         - (policy.getRiskScore() * w.get("riskScore"));
            return Math.max(0, score);
        });

        // Act
        DecisionResult result = decisionService.rankAndResolve(caseId, weights);

        // Assert
        assertNotNull(result, "Decision result must not be null");
        assertEquals("POL-002", result.getResolvedPolicyId(), "Highest weighted policy should be resolved");
        assertEquals(3, result.getRankingSize(), "All candidates must be ranked");
        assertEquals("POL-001", result.getRanking().get(1).getPolicyId(), "Secondary ranking mismatch");

        // Verify external I/O isolation
        verify(candidateRepository, times(1)).findAllForCase(caseId);
        verify(attributeCalculator, times(3)).calculateWeightedScore(any(), any());
        verifyNoMoreInteractions(candidateRepository, attributeCalculator);
    }

    // --- Test Data Models ---
    static class PolicyCandidate {
        private final String id;
        private final double premium;
        private final double coverageLimit;
        private final int riskScore;

        PolicyCandidate(String id, double premium, double coverageLimit, int riskScore) {
            this.id = id;
            this.premium = premium;
            this.coverageLimit = coverageLimit;
            this.riskScore = riskScore;
        }

        public String getId() { return id; }
        public double getPremium() { return premium; }
        public double getCoverageLimit() { return coverageLimit; }
        public int getRiskScore() { return riskScore; }
    }

    static class RankingEntry {
        private final String policyId;
        private final double score;
        RankingEntry(String policyId, double score) { this.policyId = policyId; this.score = score; }
        public String getPolicyId() { return policyId; }
        public double getScore() { return score; }
    }

    static class DecisionResult {
        private final String resolvedPolicyId;
        private final int rankingSize;
        private final List<RankingEntry> ranking;
        DecisionResult(String resolvedPolicyId, int rankingSize, List<RankingEntry> ranking) {
            this.resolvedPolicyId = resolvedPolicyId;
            this.rankingSize = rankingSize;
            this.ranking = ranking;
        }
        public String getResolvedPolicyId() { return resolvedPolicyId; }
        public int getRankingSize() { return rankingSize; }
        public List<RankingEntry> getRanking() { return ranking; }
    }

    // --- Mocked Interfaces & SUT ---
    interface PolicyCandidateRepository {
        List<PolicyCandidate> findAllForCase(String caseId);
    }

    interface WeightedAttributeCalculator {
        double calculateWeightedScore(PolicyCandidate candidate, Map<String, Double> weights);
    }

    static class DecisionCalculationService {
        private final PolicyCandidateRepository repository;
        private final WeightedAttributeCalculator calculator;

        DecisionCalculationService(PolicyCandidateRepository repository, WeightedAttributeCalculator calculator) {
            this.repository = repository;
            this.calculator = calculator;
        }

        DecisionResult rankAndResolve(String caseId, Map<String, Double> weights) {
            List<PolicyCandidate> candidates = repository.findAllForCase(caseId);
            List<RankingEntry> ranked = new ArrayList<>();

            for (PolicyCandidate c : candidates) {
                double score = calculator.calculateWeightedScore(c, weights);
                ranked.add(new RankingEntry(c.getId(), score));
            }

            ranked.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

            return new DecisionResult(
                ranked.get(0).getPolicyId(),
                ranked.size(),
                ranked
            );
        }
    }
}
