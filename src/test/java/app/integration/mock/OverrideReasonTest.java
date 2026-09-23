package app.integration.mock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationOverrideReasonTest {

    @Mock
    private RedisCacheClient redisCacheClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SesClient sesClient;

    @InjectMocks
    private ClaimInitiationRoutingDecisionService claimInitiationRoutingDecisionService;

    @Test
    void override_reason() {
        // Arrange: Setup payload with override reason per data model
        String overrideReason = "REGULATORY_EXCEPTION_APPLIED";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "claim-init-001");
        payload.put("overrideReason", overrideReason);
        payload.put("calculationType", "ROUTING_DECISION");

        // Mock external I/O contracts (Redis, DynamoDB, SES) with TLS/least-privilege assumptions
        when(redisCacheClient.get(anyString())).thenReturn("routing_rules_v1");
        when(dynamoDbClient.getItem(anyString(), anyString())).thenReturn(Map.of("pk", "claim-init-001", "ttl", "3600"));
        doNothing().when(sesClient).sendEmail(anyString(), anyList(), anyString());

        // Act: Invoke calculation & routing logic
        Map<String, Object> validationResult = claimInitiationRoutingDecisionService.processClaimInitiation(payload);

        // Assert: Verify override reason is correctly propagated and validated
        assertNotNull(validationResult, "Validation result must not be null");
        assertEquals(overrideReason, validationResult.get("overrideReason"), "Override reason must match input");
        assertTrue((Boolean) validationResult.get("isCalculated"), "Calculation flag must be true");

        // Verify infra I/O contracts were called exactly once
        verify(redisCacheClient, times(1)).get(anyString());
        verify(dynamoDbClient, times(1)).getItem(anyString(), anyString());
        verify(sesClient, times(1)).sendEmail(anyString(), anyList(), anyString());
    }
}
