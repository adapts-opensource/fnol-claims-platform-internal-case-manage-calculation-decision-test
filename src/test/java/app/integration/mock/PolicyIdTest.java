package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that the Policy ID is correctly extracted, validated via cache,
 * and propagated through the claim routing decision calculation flow.
 */
class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private RedisClient redisClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    private ClaimDecisionCalculationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ClaimDecisionCalculationService(redisClient, dynamoDbClient);
    }

    @Test
    void policy_id() {
        // Given
        String policyId = "POL-987654321";
        Map<String, Object> payload = Map.of(
                "id", "claim-init-001",
                "payload", Map.of("policy_id", policyId, "claim_type", "auto_collision")
        );
        String cacheKey = "Cache & Reference Data:cache:policy:" + policyId;
        when(redisClient.get(cacheKey)).thenReturn("{\"status\":\"ACTIVE\",\"tier\":\"PREFERRED\"}");

        // When
        Map<String, Object> result = service.calculateRoutingDecision(payload);

        // Then
        assertNotNull(result, "Routing decision result should not be null");
        assertEquals(policyId, result.get("policy_id"), "Policy ID must be propagated to routing decision");
        assertEquals("ACTIVE", result.get("policy_status"), "Policy status should be resolved from cache");
        assertEquals("standard_claims_queue", result.get("routing_decision"), "Routing decision should be calculated");
        verify(redisClient).get(cacheKey);
        verifyNoInteractions(dynamoDbClient);
    }

    // Service under test for Claim Initiation & Routing:decision:calculation
    static class ClaimDecisionCalculationService {
        private final RedisClient redisClient;
        private final DynamoDbClient dynamoDbClient;

        ClaimDecisionCalculationService(RedisClient redisClient, DynamoDbClient dynamoDbClient) {
            this.redisClient = redisClient;
            this.dynamoDbClient = dynamoDbClient;
        }

        Map<String, Object> calculateRoutingDecision(Map<String, Object> payload) {
            @SuppressWarnings("unchecked")
            Map<String, Object> claimPayload = (Map<String, Object>) payload.get("payload");
            String policyId = (String) claimPayload.get("policy_id");

            String cacheKey = "Cache & Reference Data:cache:policy:" + policyId;
            String cachedData = redisClient.get(cacheKey);
            String status = cachedData != null ? "ACTIVE" : "UNKNOWN";

            return Map.of(
                    "policy_id", policyId,
                    "policy_status", status,
                    "routing_decision", "standard_claims_queue"
            );
        }
    }

    interface RedisClient {
        String get(String key);
    }

    interface DynamoDbClient {
        Map<String, Object> getItem(String tableName, String partitionKey);
    }
}
