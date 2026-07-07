package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for Claim Initiation & Routing:decision:calculation.
 * Verifies routing rules evaluate correctly and consistently under various conditions.
 */
@ExtendWith(MockitoExtension.class)
public class RoutingRulesEvaluateCorrectlyAndConsistentlyTest {

    @Mock
    private RoutingRuleEvaluator routingRuleEvaluator;

    @Mock
    private CacheService cacheService;

    @Mock
    private DataStoreClient dataStoreClient;

    @InjectMocks
    private DecisionCalculationService decisionCalculationService;

    @Test
    void routing_rules_evaluate_correctly_and_consistently() {
        // Given: Valid claim payload representing a high-severity auto collision with injury
        String claimId = "CLM-2024-001";
        Map<String, Object> payload = Map.of(
                "claimType", "AUTO_COLLISION",
                "estimatedLoss", 15000.00,
                "injuryFlag", true,
                "priorityScore", 85,
                "policyStatus", "ACTIVE"
        );

        // Mock the rule evaluation outcome
        RoutingDecision expectedDecision = new RoutingDecision(
                "QUEUE_SPECIALIZED_AUTO",
                "PRIORITY_HIGH",
                "HANDLER_EXPERT_ADJUSTER"
        );

        when(routingRuleEvaluator.evaluate(payload)).thenReturn(expectedDecision);

        // When: Calculate decision synchronously
        RoutingDecision resultSync = decisionCalculationService.calculate(claimId, payload);

        // Then: Verify correctness of the calculated decision
        assertNotNull(resultSync);
        assertEquals("QUEUE_SPECIALIZED_AUTO", resultSync.getQueue());
        assertEquals("PRIORITY_HIGH", resultSync.getPriority());
        assertEquals("HANDLER_EXPERT_ADJUSTER", resultSync.getHandler());

        // Consistency: Idempotency check - same input must yield same result
        RoutingDecision resultSync2 = decisionCalculationService.calculate(claimId, payload);
        assertEquals(resultSync, resultSync2);

        // Concurrency & Thread Safety: Verify consistent results under parallel execution
        CompletableFuture<RoutingDecision> future1 = CompletableFuture.supplyAsync(() ->
                decisionCalculationService.calculate(claimId, payload));
        CompletableFuture<RoutingDecision> future2 = CompletableFuture.supplyAsync(() ->
                decisionCalculationService.calculate(claimId, payload));

        RoutingDecision resultAsync1 = future1.join();
        RoutingDecision resultAsync2 = future2.join();

        assertEquals(resultSync, resultAsync1);
        assertEquals(resultSync, resultAsync2);

        // Verify mock interactions occurred as expected (2 sync + 2 async calls)
        verify(routingRuleEvaluator, times(4)).evaluate(payload);
    }
}
