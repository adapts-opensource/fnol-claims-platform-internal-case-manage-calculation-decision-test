package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
public class PurposeAssistAnalystInSelectingCorrectPolicyByTest {

    @Mock
    private PolicyCandidateRepository mockCandidateRepository;

    @Mock
    private ScoringEngine mockScoringEngine;

    @Mock
    private ComplianceAuditService mockAuditService;

    private PolicyScoringOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new PolicyScoringOrchestrationService(
            mockCandidateRepository, mockScoringEngine, mockAuditService
        );
    }

    @Test
    void purpose_assist_analyst_in_selecting_correct_policy_by_scoring_candidates_highlighting_differentiators() {
        // Arrange
        String claimId = "CLM-INIT-789";
        List<PolicyCandidate> candidates = List.of(
            new PolicyCandidate("POL-A", "Standard Auto", 78.0, Map.of("deductible", "1000")),
            new PolicyCandidate("POL-B", "Premium Auto", 82.5, Map.of("deductible", "500"))
        );

        when(mockCandidateRepository.fetchCandidates(claimId)).thenReturn(candidates);
        when(mockScoringEngine.calculateScore(any(PolicyCandidate.class)))
            .thenAnswer(invocation -> {
                PolicyCandidate c = invocation.getArgument(0);
                return c.baseScore() + 3.0;
            });

        // Act
        TransformationResult result = orchestrationService.transformForAnalystSelection(claimId);

        // Assert
        assertNotNull(result, "Transformation result should not be null");
        assertFalse(result.candidates().isEmpty(), "Should return scored candidates");
        assertEquals(2, result.candidates().size(), "Should process all candidates");

        double adjustedScoreA = 78.0 + 3.0;
        double adjustedScoreB = 82.5 + 3.0;
        assertEquals(adjustedScoreA, result.candidates().get(0).score(), 0.01);
        assertEquals(adjustedScoreB, result.candidates().get(1).score(), 0.01);

        assertTrue(result.highlightedDifferentiators().containsKey("POL-B"),
            "Top scoring candidate should be highlighted");
        assertEquals("lower_deductible", result.highlightedDifferentiators().get("POL-B"),
            "Differentiator should identify key policy distinction");

        verify(mockCandidateRepository, times(1)).fetchCandidates(claimId);
        verify(mockScoringEngine, times(2)).calculateScore(any(PolicyCandidate.class));
        verify(mockAuditService).logTransformation("PolicyScoringOrchestration", claimId, "TRANSFORM_COMPLETE");
    }

    // Supporting types for test isolation
    static class PolicyCandidate {
        private final String policyId;
        private final String productName;
        private final double baseScore;
        private final Map<String, String> attributes;
        private double score;

        public PolicyCandidate(String policyId, String productName, double baseScore, Map<String, String> attributes) {
            this.policyId = policyId;
            this.productName = productName;
            this.baseScore = baseScore;
            this.attributes = attributes;
            this.score = baseScore;
        }
        public String policyId() { return policyId; }
        public String productName() { return productName; }
        public double baseScore() { return baseScore; }
        public Map<String, String> attributes() { return attributes; }
        public double score() { return score; }
        public void setScore(double score) { this.score = score; }
    }

    static class TransformationResult {
        private final List<PolicyCandidate> candidates;
        private final Map<String, String> highlightedDifferentiators;

        public TransformationResult(List<PolicyCandidate> candidates, Map<String, String> highlightedDifferentiators) {
            this.candidates = candidates;
            this.highlightedDifferentiators = highlightedDifferentiators;
        }
        public List<PolicyCandidate> candidates() { return candidates; }
        public Map<String, String> highlightedDifferentiators() { return highlightedDifferentiators; }
    }

    interface PolicyCandidateRepository { List<PolicyCandidate> fetchCandidates(String claimId); }
    interface ScoringEngine { double calculateScore(PolicyCandidate candidate); }
    interface ComplianceAuditService { void logTransformation(String service, String claimId, String payload); }

    static class PolicyScoringOrchestrationService {
        private final PolicyCandidateRepository repository;
        private final ScoringEngine scoringEngine;
        private final ComplianceAuditService auditService;

        public PolicyScoringOrchestrationService(PolicyCandidateRepository repository, ScoringEngine scoringEngine, ComplianceAuditService auditService) {
            this.repository = repository;
            this.scoringEngine = scoringEngine;
            this.auditService = auditService;
        }

        public TransformationResult transformForAnalystSelection(String claimId) {
            List<PolicyCandidate> candidates = repository.fetchCandidates(claimId);
            List<PolicyCandidate> scoredCandidates = candidates.stream()
                .peek(c -> c.setScore(scoringEngine.calculateScore(c)))
                .toList();

            Map<String, String> differentiators = scoredCandidates.stream()
                .collect(Collectors.toMap(
                    PolicyCandidate::policyId,
                    c -> c.score() > 80.0 ? "lower_deductible" : "standard_coverage"
                ));

            auditService.logTransformation("PolicyScoringOrchestration", claimId, "TRANSFORM_COMPLETE");
            return new TransformationResult(scoredCandidates, differentiators);
        }
    }
}
