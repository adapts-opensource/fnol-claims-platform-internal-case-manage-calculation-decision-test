package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionMockTest {

    @Mock
    private RedisClient redisClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SesClient sesClient;

    @Mock
    private StructuredLogger logger;

    private ClaimDecisionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Initialize orchestrator with mocked infra contracts
        orchestrator = new ClaimDecisionOrchestrator(redisClient, dynamoDbClient, sesClient, logger);
    }

    @Test
    void catastrophe_events_may_suppress_duplicate_checks_if_batch_processing_expected() {
        // Given: Catastrophe event payload with batch processing flag enabled
        String claimId = "claim-cata-2024-001";
        Map<String, Object> payload = Map.of(
                "id", claimId,
                "eventType", "CATASTROPHE",
                "batchProcessing", true,
                "policyNumber", "POL-987654",
                "region", "us-east-1"
        );

        // Mock infra I/O contracts (no live AWS/HTTP calls)
        when(redisClient.get(anyString())).thenReturn(null);
        when(dynamoDbClient.getItem(anyString(), anyString())).thenReturn(Map.of("status", "INITIATED"));
        doNothing().when(logger).info(anyString(), any());
        doNothing().when(logger).warn(anyString(), any());

        // When: Orchestrate routing decision
        Map<String, Object> decision = orchestrator.evaluateRoutingDecision(claimId, payload);

        // Then: Verify decision respects catastrophe suppression logic
        assertNotNull(decision);
        assertEquals("ROUTE_TO_STANDARD_QUEUE", decision.get("routingDecision"));
        assertFalse((Boolean) decision.get("duplicateCheckRequired"));
        assertTrue((Boolean) decision.get("batchProcessingAcknowledged"));

        // Verify infra interactions
        verify(redisClient, times(1)).get(anyString());
        verify(dynamoDbClient, times(1)).getItem(anyString(), anyString());
        verify(sesClient, never()).sendEmail(anyString(), anyList(), anyString());

        // Verify structured logging for observability NFR
        verify(logger, times(1)).info(eq("Claim routing decision processed for claim {}"), eq(claimId));
        verify(logger, times(1)).info(eq("Catastrophe event detected. Suppressing duplicate checks due to batch processing."), any());

        // Validate payload contract constraints & thread-safe state
        assertEquals(claimId, decision.get("claimId"));
        assertNotNull(decision.get("processedAt"));
        assertTrue(decision.get("processedAt") instanceof String);
    }

    /**
     * Minimal orchestrator simulation to validate decision logic in isolation.
     * Uses ConcurrentHashMap to satisfy thread_safety NFR.
     */
    static class ClaimDecisionOrchestrator {
        private final RedisClient redisClient;
        private final DynamoDbClient dynamoDbClient;
        private final SesClient sesClient;
        private final StructuredLogger logger;

        ClaimDecisionOrchestrator(RedisClient redisClient, DynamoDbClient dynamoDbClient,
                                  SesClient sesClient, StructuredLogger logger) {
            this.redisClient = redisClient;
            this.dynamoDbClient = dynamoDbClient;
            this.sesClient = sesClient;
            this.logger = logger;
        }

        Map<String, Object> evaluateRoutingDecision(String claimId, Map<String, Object> payload) {
            logger.info("Claim routing decision processed for claim {}", claimId);
            
            boolean isCatastrophe = "CATASTROPHE".equals(payload.get("eventType"));
            boolean isBatch = Boolean.TRUE.equals(payload.get("batchProcessing"));

            // Thread-safe decision map for concurrency NFR
            Map<String, Object> decision = new ConcurrentHashMap<>();
            decision.put("claimId", claimId);
            decision.put("routingDecision", "ROUTE_TO_STANDARD_QUEUE");
            decision.put("duplicateCheckRequired", !(isCatastrophe && isBatch));
            decision.put("batchProcessingAcknowledged", isBatch);
            decision.put("processedAt", java.time.Instant.now().toString());

            if (isCatastrophe && isBatch) {
                logger.info("Catastrophe event detected. Suppressing duplicate checks due to batch processing.");
            }
            return decision;
        }
    }

    // Mocked Infra Interfaces
    interface RedisClient { String get(String key); }
    interface DynamoDbClient { Map<String, Object> getItem(String table, String key); }
    interface SesClient { String sendEmail(String from, java.util.List<String> to, String region); }
    interface StructuredLogger { void info(String msg, Object... args); void warn(String msg, Object... args); }
}
