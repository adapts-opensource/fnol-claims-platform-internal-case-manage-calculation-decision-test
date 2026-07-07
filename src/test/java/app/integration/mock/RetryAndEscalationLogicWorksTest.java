package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RetryAndEscalationLogicWorksTest {

    @Mock
    private RedisClient redisClient;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private SesClient sesClient;
    private ClaimRoutingDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new ClaimRoutingDecisionService(redisClient, dynamoDbClient, sesClient);
    }

    @Test
    void retry_and_escalation_logic_works() {
        // Arrange
        String claimId = "claim-init-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("status", "CALCULATING");
        payload.put("retryCount", 0);

        AtomicInteger attemptCounter = new AtomicInteger(0);
        when(redisClient.get(anyString())).thenAnswer(invocation -> {
            int attempt = attemptCounter.incrementAndGet();
            if (attempt <= 2) {
                return "PENDING_RETRY";
            }
            return "COMPLETED";
        });

        when(dynamoDbClient.getItem(any())).thenReturn(payload);
        when(sesClient.sendEmail(anyString(), any(), anyString())).thenReturn("ses-msg-id-123");

        // Act
        Map<String, Object> result = decisionService.calculateAndRouteClaim(claimId, payload);

        // Assert
        assertNotNull(result, "Result should not be null after successful retry logic");
        assertEquals("ROUTED", result.get("status"), "Claim should be routed after successful calculation");
        assertEquals(3, attemptCounter.get(), "Service should attempt calculation three times (initial + 2 retries)");
        assertEquals(2, result.get("retryCount"), "Retry count should reflect two retries");
        assertEquals("ESCALATED", result.get("escalationStatus"), "Escalation should be triggered after max retries");

        verify(redisClient, times(3)).get(anyString());
        verify(dynamoDbClient, times(1)).getItem(any());
        verify(sesClient, times(1)).sendEmail(anyString(), any(), anyString());
        verifyNoMoreInteractions(redisClient, dynamoDbClient, sesClient);
    }
}
