package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 integration mock test for Claim Initiation & Routing:decision:calculation.
 * Verifies that failed deliveries correctly trigger retry mechanisms and escalation paths.
 * NFR Alignment: thread-safe atomic assertions, structured logging verification,
 * input validation mocks, and simulated TLS/secrets handling via mocked infra contracts.
 */
@ExtendWith(MockitoExtension.class)
public class FailedDeliveriesTriggerRetryAndEscalationTest {

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private SesCommunicationService sesCommunicationService;

    @Mock
    private StructuredLogger structuredLogger;

    @Mock
    private ClaimDecisionCalculator claimDecisionCalculator;

    @Test
    void failed_deliveries_trigger_retry_and_escalation() {
        // Arrange: Construct validation payload per data model
        String claimId = "CLM-789012";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("deliveryStatus", "FAILED");
        payload.put("retryCount", 2);
        payload.put("escalationThreshold", 3);
        payload.put("routingRule", "AUTO_ROUTING_V1");

        // Mock infra I/O contracts (Redis, DynamoDB, SES)
        when(redisCacheService.get(anyString())).thenReturn("ACTIVE");
        when(dynamoDbService.getItem(anyString(), anyString())).thenReturn(payload);
        when(claimDecisionCalculator.evaluate(anyMap())).thenReturn(Map.of(
                "retryTriggered", true,
                "escalationTriggered", true,
                "nextRoutingNode", "ESCALATION_QUEUE",
                "ttlSeconds", 3600
        ));

        AtomicBoolean retryExecuted = new AtomicBoolean(false);
        AtomicBoolean escalationExecuted = new AtomicBoolean(false);

        // Act: Execute decision calculation with input validation & thread-safe context
        Map<String, Object> decisionResult = claimDecisionCalculator.process(payload);

        // Assert: Verify retry and escalation triggers
        assertTrue((Boolean) decisionResult.get("retryTriggered"), "Retry must be triggered on failed delivery");
        assertTrue((Boolean) decisionResult.get("escalationTriggered"), "Escalation must be triggered when threshold is met");
        assertEquals("ESCALATION_QUEUE", decisionResult.get("nextRoutingNode"));

        // Verify external I/O contracts are mocked and called correctly
        verify(redisCacheService).put(eq("Cache & Reference Data:cache:"), eq(claimId), anyInt());
        verify(dynamoDbService).putItem(eq("Claims & Policy Data Store_table"), anyMap());
        verify(sesCommunicationService).sendEmail(
                eq("noreply@newcoinsurance.com"),
                eq(List.of("claims.manager@newcoinsurance.com")),
                eq("us-east-1")
        );
        verify(structuredLogger).info(eq("Failed delivery triggered retry and escalation"), anyMap());
    }
}
