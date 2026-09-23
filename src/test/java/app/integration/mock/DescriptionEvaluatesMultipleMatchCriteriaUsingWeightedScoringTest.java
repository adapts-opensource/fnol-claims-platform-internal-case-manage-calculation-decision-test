package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

public class ClaimInitiationOrchestrationMockTest {

    private PolicyMatchingService mockPolicyMatchingService;
    private ClaimTransformationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        mockPolicyMatchingService = mock(PolicyMatchingService.class);
        orchestrator = new ClaimTransformationOrchestrator(mockPolicyMatchingService);
    }

    @Test
    void description_evaluates_multiple_match_criteria_using_weighted_scoring_to_identify_the_best_policy_match() {
        // Arrange: Define claim initiation payload and mock weighted scoring results
        String claimId = "CLM-2024-001";
        String vin = "JH4KA7561PC000001";
        String policyholderId = "PH-55432";

        // Mock policy candidates with different match criteria weights
        List<PolicyMatchCriteria> candidates = List.of(
            new PolicyMatchCriteria("POL-100", policyholderId, null, 0.75),
            new PolicyMatchCriteria("POL-200", null, vin, 0.88),
            new PolicyMatchCriteria("POL-300", policyholderId, vin, 0.95)
        );

        when(mockPolicyMatchingService.fetchAndScoreCandidates(claimId, vin, policyholderId))
            .thenReturn(candidates);

        // Act: Execute transformation and routing orchestration
        PolicyMatchResult result = orchestrator.transformAndRoute(claimId, vin, policyholderId);

        // Assert: Verify best match is identified based on highest weighted score
        assertNotNull(result);
        assertEquals("POL-300", result.bestMatchPolicyId());
        assertEquals(0.95, result.weightedScore(), 0.001);
        assertEquals("Routed to ClaimsAdjudicationQueue", result.routingDestination());

        verify(mockPolicyMatchingService, times(1)).fetchAndScoreCandidates(claimId, vin, policyholderId);
    }

    // Mock Service Interface (simulates AWS DynamoDB/S3 policy lookup)
    interface PolicyMatchingService {
        List<PolicyMatchCriteria> fetchAndScoreCandidates(String claimId, String vin, String policyholderId);
    }

    // Data Transfer Objects
    record PolicyMatchCriteria(String policyId, String policyholderId, String vin, double score) {}
    record PolicyMatchResult(String bestMatchPolicyId, double weightedScore, String routingDestination) {}

    // Component Under Test: Orchestration & Transformation Layer
    static class ClaimTransformationOrchestrator {
        private final PolicyMatchingService policyMatchingService;

        ClaimTransformationOrchestrator(PolicyMatchingService policyMatchingService) {
            this.policyMatchingService = policyMatchingService;
        }

        PolicyMatchResult transformAndRoute(String claimId, String vin, String policyholderId) {
            List<PolicyMatchCriteria> candidates = policyMatchingService.fetchAndScoreCandidates(claimId, vin, policyholderId);
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("No policy matches found for claim: " + claimId);
            }
            PolicyMatchCriteria bestMatch = candidates.stream()
                .max((a, b) -> Double.compare(a.score(), b.score()))
                .orElseThrow();

            return new PolicyMatchResult(
                bestMatch.policyId(),
                bestMatch.score(),
                "Routed to ClaimsAdjudicationQueue"
            );
        }
    }
}
