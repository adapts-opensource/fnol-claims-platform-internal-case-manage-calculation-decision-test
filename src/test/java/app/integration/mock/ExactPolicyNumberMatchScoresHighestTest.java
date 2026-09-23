package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;

// Abstracts external DynamoDB PolicyClaimsDB for test isolation
interface PolicyClaimsRepository {
    List<Map<String, Object>> queryByPolicyNumber(String policyNumber);
}

// Core orchestration & transformation logic under test
class ClaimRoutingOrchestrator {
    private final PolicyClaimsRepository policyClaimsRepository;

    public ClaimRoutingOrchestrator(PolicyClaimsRepository policyClaimsRepository) {
        this.policyClaimsRepository = policyClaimsRepository;
    }

    public Map<String, Object> orchestrateAndTransform(String claimId, String inputPolicyNumber) {
        List<Map<String, Object>> candidates = policyClaimsRepository.queryByPolicyNumber(inputPolicyNumber);
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No policy candidates found for claim " + claimId);
        }

        Map<String, Object> bestMatch = null;
        double highestScore = Double.MIN_VALUE;

        for (Map<String, Object> candidate : candidates) {
            String dbPolicyNumber = (String) candidate.get("pk");
            // Scoring rule: exact match scores highest (1.0), others score lower (0.5)
            double score = inputPolicyNumber.equals(dbPolicyNumber) ? 1.0 : 0.5;
            candidate.put("matchScore", score);

            if (score > highestScore) {
                highestScore = score;
                bestMatch = candidate;
            }
        }
        return bestMatch;
    }
}

@org.junit.jupiter.api.DisplayName("Claim Initiation & Routing: orchestration: transformation")
class ClaimInitiationRoutingOrchestrationTransformationTest {

    private PolicyClaimsRepository mockPolicyClaimsRepository;
    private ClaimRoutingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        mockPolicyClaimsRepository = mock(PolicyClaimsRepository.class);
        orchestrator = new ClaimRoutingOrchestrator(mockPolicyClaimsRepository);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Exact policy number match scores highest")
    void exact_policy_number_match_scores_highest() {
        // Arrange
        String inputPolicyNumber = "POL-987654321";
        List<Map<String, Object>> mockCandidates = List.of(
                Map.of("pk", "POL-98765432", "status", "ACTIVE"),
                Map.of("pk", "POL-987654321", "status", "ACTIVE"),
                Map.of("pk", "POL-987654322", "status", "PENDING")
        );
        when(mockPolicyClaimsRepository.queryByPolicyNumber(inputPolicyNumber)).thenReturn(mockCandidates);

        // Act
        Map<String, Object> result = orchestrator.orchestrateAndTransform("claim-init-001", inputPolicyNumber);

        // Assert
        assertNotNull(result, "Routing result should not be null");
        assertEquals("POL-987654321", result.get("pk"), "Exact match should be selected");
        assertEquals(1.0, result.get("matchScore"), "Exact match should score highest");
        verify(mockPolicyClaimsRepository).queryByPolicyNumber(inputPolicyNumber);
    }
}
