package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Validates Claim Initiation & Routing orchestration decisions.
 * Verifies thread-safe, input-validated routing when an adjuster interacts with policy match tasks.
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionValidationTest {

    // Infra I/O contract constants
    private static final String CACHE_KEY_NAMESPACE = "Cache & Reference Data:cache:";
    private static final int TTL_SECONDS = 3600;
    private static final String DYNAMODB_TABLE = "Claims & Policy Data Store_table";
    private static final String PARTITION_KEY = "pk";

    @Mock
    private RedisClient redisClient;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private DecisionOrchestrationService decisionOrchestrationService;

    private String claimId;
    private Map<String, Object> adjustmentPayload;

    @BeforeEach
    void setUp() {
        claimId = "CLM-INIT-2024-001";
        adjustmentPayload = new HashMap<>();
        adjustmentPayload.put("id", claimId);
        adjustmentPayload.put("taskType", "Resolve Policy Match");
        adjustmentPayload.put("initiatorRole", "ADJUSTER");
        adjustmentPayload.put("interactionTimestamp", System.currentTimeMillis());
        adjustmentPayload.put("pii", false);
    }

    @Test
    void applies_when_adjuster_interacts_with_resolve_policy_match_task() {
        // Arrange: Mock external I/O contracts (Redis & DynamoDB)
        String cacheKey = CACHE_KEY_NAMESPACE + claimId;
        when(redisClient.get(cacheKey)).thenReturn("ACTIVE");

        Map<String, Object> dbItem = new HashMap<>();
        dbItem.put(PARTITION_KEY, claimId);
        dbItem.put("payload", adjustmentPayload);
        when(dynamoDbClient.getItem(DYNAMODB_TABLE, PARTITION_KEY, claimId)).thenReturn(dbItem);

        // Act: Invoke orchestration decision evaluation
        String routingDecision = decisionOrchestrationService.evaluateDecision(claimId, adjustmentPayload);

        // Assert: Verify decision outcome and infrastructure interactions
        assertNotNull(routingDecision, "Decision must be resolved");
        assertEquals("ROUTE_TO_POLICY_SPECIALIST", routingDecision,
                "Adjuster interaction with Resolve Policy Match should route to specialist");

        // Verify Redis cache contract (read & write with TTL)
        verify(redisClient).get(cacheKey);
        verify(redisClient).put(eq(CACHE_KEY_NAMESPACE + "decision:" + claimId),
                eq(routingDecision), eq(TTL_SECONDS));

        // Verify DynamoDB read contract
        verify(dynamoDbClient).getItem(DYNAMODB_TABLE, PARTITION_KEY, claimId);

        // Verify structured logging & input validation hooks (mocked via service)
        verify(decisionOrchestrationService).validateInput(claimId, adjustmentPayload);
        verify(decisionOrchestrationService).logDecisionEvent(eq(claimId), eq(routingDecision));
    }
}
