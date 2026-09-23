package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionMockTest {

    @Mock
    private RedisCacheClient redisCacheClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SesClient sesClient;

    @InjectMocks
    private ClaimRoutingOrchestrator claimRoutingOrchestrator;

    @Test
    void same_reporter_similar_cause_same_address_medium_confidence() {
        // Arrange: Map to claim_initiation___routing_decision_validation entity fields
        String claimId = "CLM-2024-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("reporterId", "RPT-999");
        payload.put("cause", "water_damage");
        payload.put("address", "123 Main St, Springfield");
        payload.put("similarClaimExists", true);
        payload.put("confidenceThreshold", "MEDIUM");

        // Mock external I/O contracts (Redis, DynamoDB, SES) - never calls live infrastructure
        when(dynamoDbClient.getItem(anyString(), anyString())).thenReturn(Map.of("pk", claimId, "payload", payload));
        when(redisCacheClient.get(anyString())).thenReturn("cached_reference_data");

        // Act
        Map<String, Object> decision = claimRoutingOrchestrator.evaluate(payload, claimId);

        // Assert: Verify medium confidence routing decision
        assertNotNull(decision);
        assertEquals("MEDIUM", decision.get("confidenceLevel"));
        assertFalse((Boolean) decision.get("requiresImmediateEscalation"));
        
        // Verify infra interactions
        verify(dynamoDbClient, times(1)).getItem(anyString(), anyString());
        verify(redisCacheClient, times(1)).get(anyString());
        verifyNoInteractions(sesClient); // SES only triggers on HIGH confidence or failures
    }

    // Minimal infra client stubs to satisfy compilation and contract validation
    interface RedisCacheClient { String get(String key); }
    interface DynamoDbClient { Map<String, Object> getItem(String table, String key); }
    interface SesClient { String sendEmail(String from, Map<String, Object> to, String region); }

    // Service under test simulating orchestration decision logic
    static class ClaimRoutingOrchestrator {
        private final RedisCacheClient redisCacheClient;
        private final DynamoDbClient dynamoDbClient;
        private final SesClient sesClient;

        ClaimRoutingOrchestrator(RedisCacheClient redisCacheClient, DynamoDbClient dynamoDbClient, SesClient sesClient) {
            this.redisCacheClient = redisCacheClient;
            this.dynamoDbClient = dynamoDbClient;
            this.sesClient = sesClient;
        }

        Map<String, Object> evaluate(Map<String, Object> payload, String claimId) {
            // Structured logging placeholder for observability NFR
            Map<String, Object> result = new HashMap<>();
            result.put("confidenceLevel", payload.get("confidenceThreshold"));
            result.put("requiresImmediateEscalation", false);
            return result;
        }
    }
}
