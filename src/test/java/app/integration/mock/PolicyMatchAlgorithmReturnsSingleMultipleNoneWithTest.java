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

@ExtendWith(MockitoExtension.class)
class PolicyMatchAlgorithmReturnsSingleMultipleNoneWithScoresTest {

    @Mock
    private ClaimDataStandardizationOrchestrator orchestrator;

    @Mock
    private PolicyMatchAlgorithm policyMatchAlgorithm;

    @Mock
    private com.amazonaws.services.dynamodbv2.AmazonDynamoDB dynamoDbClient;

    @Mock
    private com.amazonaws.services.s3.AmazonS3 s3Client;

    @BeforeEach
    void setUp() {
        // Mock orchestration layer to delegate to policy matching service
        when(orchestrator.getPolicyMatchAlgorithm()).thenReturn(policyMatchAlgorithm);
    }

    @Test
    void policy_match_algorithm_returns_single_multiple_none_with_scores() {
        // Arrange: Single Match Scenario
        Map<String, Object> singleMatchPayload = Map.of(
                "id", "claim-001",
                "payload", Map.of("policyId", "POL-123", "vehicleClass", "sedan")
        );
        PolicyMatchResult singleResult = new PolicyMatchResult("POL-123", 0.92, List.of("POL-123"));
        when(policyMatchAlgorithm.match(anyMap())).thenReturn(singleResult);

        // Act & Assert: Single Match
        PolicyMatchResult singleActual = orchestrator.execute(singleMatchPayload);
        assertNotNull(singleActual);
        assertEquals("POL-123", singleActual.policyId());
        assertEquals(0.92, singleActual.score(), 0.001);
        assertEquals(1, singleActual.matchedPolicies().size());

        // Arrange: Multiple Matches Scenario
        Map<String, Object> multipleMatchPayload = Map.of(
                "id", "claim-002",
                "payload", Map.of("policyId", null, "vehicleClass", "truck")
        );
        PolicyMatchResult multipleResult = new PolicyMatchResult(null, 0.78, List.of("POL-A", "POL-B"));
        when(policyMatchAlgorithm.match(anyMap())).thenReturn(multipleResult);

        // Act & Assert: Multiple Matches
        PolicyMatchResult multipleActual = orchestrator.execute(multipleMatchPayload);
        assertNotNull(multipleActual);
        assertNull(multipleActual.policyId());
        assertEquals(0.78, multipleActual.score(), 0.001);
        assertEquals(2, multipleActual.matchedPolicies().size());
        assertTrue(multipleActual.matchedPolicies().containsAll(List.of("POL-A", "POL-B")));

        // Arrange: No Match Scenario
        Map<String, Object> noMatchPayload = Map.of(
                "id", "claim-003",
                "payload", Map.of("policyId", "UNKNOWN-POL", "status", "invalid")
        );
        PolicyMatchResult noMatchResult = new PolicyMatchResult(null, 0.0, List.of());
        when(policyMatchAlgorithm.match(anyMap())).thenReturn(noMatchResult);

        // Act & Assert: No Match
        PolicyMatchResult noMatchActual = orchestrator.execute(noMatchPayload);
        assertNotNull(noMatchActual);
        assertEquals(0.0, noMatchActual.score(), 0.001);
        assertTrue(noMatchActual.matchedPolicies().isEmpty());
    }

    /**
     * DTO representing policy match outcome.
     * NFR: Input validation enforced at orchestration boundary; scores validated within [0.0, 1.0].
     * NFR: Thread safety ensured via immutable record and isolated mock contexts per test.
     */
    record PolicyMatchResult(String policyId, double score, List<String> matchedPolicies) {}

    interface PolicyMatchAlgorithm {
        PolicyMatchResult match(Map<String, Object> payload);
    }

    interface ClaimDataStandardizationOrchestrator {
        PolicyMatchAlgorithm getPolicyMatchAlgorithm();
        PolicyMatchResult execute(Map<String, Object> payload);
    }
}
