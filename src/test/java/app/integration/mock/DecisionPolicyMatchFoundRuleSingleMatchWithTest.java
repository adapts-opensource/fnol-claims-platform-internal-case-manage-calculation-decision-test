package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Mock integration test for Claim Data Standardization:state_transition:orchestration.
 * Validates routing logic against mocked DynamoDB and S3 I/O contracts.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationStateTransitionOrchMockTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    private ClaimStateTransitionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimStateTransitionOrchestrator(dynamoDbClient, s3Client);
    }

    @Test
    void decision_policy_match_found_rule_single_match_with_dol_within_period_coverage_valid_expected_outcome_route_to_standard_triage() {
        // Given
        String claimId = "claim-123";
        Map<String, Object> payload = new HashMap<>();
        payload.put("policyMatch", true);
        payload.put("policyMatches", 1);
        payload.put("dateOfLoss", "2023-10-01");
        payload.put("periodStart", "2023-01-01");
        payload.put("periodEnd", "2024-01-01");
        payload.put("coverage", "valid");

        ClaimDataStandardizationStateTransitionOrch input = new ClaimDataStandardizationStateTransitionOrch(claimId, payload);

        // Mock DynamoDB query for claim retrieval (infra contract: Claim Data Store)
        when(dynamoDbClient.query(any())).thenReturn(null);

        // When
        StateTransitionResult result = orchestrator.processClaim(input);

        // Then
        assertNotNull(result, "Orchestration result must not be null");
        assertEquals("STANDARD_TRIAGE", result.getRoute(), "Single match with DoL within period and valid coverage routes to standard triage");
        assertTrue(result.isCoverageValid(), "Coverage flag must be marked valid");
        verify(dynamoDbClient, times(1)).query(any());
    }

    /**
     * Immutable model representing the claim data standardization state transition entity.
     * Thread-safe by design (final fields, no mutable state).
     */
    static final class ClaimDataStandardizationStateTransitionOrch {
        private final String id;
        private final Map<String, Object> payload;

        ClaimDataStandardizationStateTransitionOrch(String id, Map<String, Object> payload) {
            this.id = id;
            this.payload = Map.copyOf(payload);
        }

        String getId() { return id; }
        Map<String, Object> getPayload() { return payload; }
    }

    /**
     * Outcome model for state transition routing decisions.
     */
    static final class StateTransitionResult {
        private final String route;
        private final boolean coverageValid;

        StateTransitionResult(String route, boolean coverageValid) {
            this.route = route;
            this.coverageValid = coverageValid;
        }

        String getRoute() { return route; }
        boolean isCoverageValid() { return coverageValid; }
    }

    /**
     * Orchestrator service that processes claim data standardization state transitions.
     * Mocked dependencies ensure no live AWS calls occur during unit/integration testing.
     */
    static final class ClaimStateTransitionOrchestrator {
        private final DynamoDbClient dynamoDbClient;
        private final S3Client s3Client;

        ClaimStateTransitionOrchestrator(DynamoDbClient dynamoDbClient, S3Client s3Client) {
            this.dynamoDbClient = dynamoDbClient;
            this.s3Client = s3Client;
        }

        StateTransitionResult processClaim(ClaimDataStandardizationStateTransitionOrch claimData) {
            Map<String, Object> payload = claimData.getPayload();
            boolean policyMatch = Boolean.TRUE.equals(payload.get("policyMatch"));
            int policyMatches = payload.get("policyMatches") instanceof Number ? ((Number) payload.get("policyMatches")).intValue() : 0;
            String coverage = (String) payload.get("coverage");
            String dateOfLoss = (String) payload.get("dateOfLoss");
            String periodStart = (String) payload.get("periodStart");
            String periodEnd = (String) payload.get("periodEnd");

            boolean dolWithinPeriod = dateOfLoss != null && periodStart != null && periodEnd != null
                    && dateOfLoss.compareTo(periodStart) >= 0 && dateOfLoss.compareTo(periodEnd) <= 0;

            if (policyMatch && policyMatches == 1 && dolWithinPeriod && "valid".equalsIgnoreCase(coverage)) {
                return new StateTransitionResult("STANDARD_TRIAGE", true);
            }
            return new StateTransitionResult("REVIEW", false);
        }
    }
}
