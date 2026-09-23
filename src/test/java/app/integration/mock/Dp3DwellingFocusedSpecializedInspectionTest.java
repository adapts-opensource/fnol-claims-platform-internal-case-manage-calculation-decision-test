package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Initiation & Routing:decision:calculation.
 * Verifies DP3 dwelling-focused routing to specialized inspection.
 */
@ExtendWith(MockitoExtension.class)
class Dp3DwellingFocusedSpecializedInspectionTest {

    @Mock
    private RedisClient redisClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private RoutingDecisionCalculator routingDecisionCalculator;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    @Captor
    private ArgumentCaptor<String> structuredLogCaptor;

    private Map<String, Object> dp3DwellingPayload;

    @BeforeEach
    void setUp() {
        dp3DwellingPayload = Map.of(
            "id", "claim-456",
            "policyType", "DP3",
            "propertyType", "dwelling",
            "inspectionType", "focused",
            "payload", Map.of("coverageLevel", "standard")
        );
    }

    @Test
    void dp3_dwelling_focused_specialized_inspection() {
        // Given: Cache returns specialized_inspection for DP3 dwelling-focused claims
        String cacheKey = "Cache & Reference Data:cache:routing:dp3_dwelling";
        String expectedDecision = "specialized_inspection";
        when(redisClient.get(anyString())).thenReturn(expectedDecision);

        // When: Calculator processes the payload
        Map<String, Object> result = routingDecisionCalculator.calculateDecision(dp3DwellingPayload);

        // Then: Decision matches expected routing
        assertNotNull(result);
        assertEquals(expectedDecision, result.get("routingDecision"));
        assertEquals("claim-456", result.get("claimId"));

        // Verify infra I/O contracts (Redis hit, DynamoDB fallback skipped)
        verify(redisClient, times(1)).get(eq(cacheKey));
        verifyNoInteractions(dynamoDbClient);

        // Verify structured logging (observability NFR)
        verify(structuredLogCaptor.capture(), times(1));
        assertTrue(structuredLogCaptor.getValue().contains("routing_decision_calculated"));
    }

    @Test
    void dp3_dwelling_focused_specialized_inspection_thread_safety() {
        // Given: Concurrent claim submissions
        when(redisClient.get(anyString())).thenReturn("specialized_inspection");

        // When: Multiple threads process identical payloads
        List<CompletableFuture<Map<String, Object>>> futures = IntStream.rangeClosed(1, 10)
            .mapToObj(i -> CompletableFuture.supplyAsync(() ->
                routingDecisionCalculator.calculateDecision(dp3DwellingPayload)
            ))
            .toList();

        // Then: All complete successfully without race conditions
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        futures.forEach(f -> {
            Map<String, Object> res = f.join();
            assertEquals("specialized_inspection", res.get("routingDecision"));
        });

        // Verify thread-safe infra access
        verify(redisClient, times(10)).get(anyString());
        verifyNoMoreInteractions(redisClient, dynamoDbClient);
    }

    @Test
    void dp3_dwelling_focused_specialized_inspection_input_validation() {
        // Given: Malformed payload missing required policy/property fields
        Map<String, Object> invalidPayload = Map.of("id", "claim-789");

        // When & Then: Input validation rejects incomplete data
        assertThrows(IllegalArgumentException.class, () ->
            routingDecisionCalculator.calculateDecision(invalidPayload)
        );
    }
}
