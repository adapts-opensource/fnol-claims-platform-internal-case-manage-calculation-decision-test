package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private RedisCacheService redisCacheService;
    @Mock
    private DynamoDbService dynamoDbService;
    @Mock
    private SesCommunicationService sesCommunicationService;

    private ClaimDecisionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimDecisionOrchestrator(redisCacheService, dynamoDbService, sesCommunicationService);
    }

    @Test
    void cancellation_rejections_apply_unless_reinstated() {
        // Arrange: Claim is NOT reinstated -> cancellation rejections should apply
        Map<String, Object> payloadNotReinstated = new HashMap<>();
        payloadNotReinstated.put("id", "claim-123");
        payloadNotReinstated.put("reinstated", false);
        payloadNotReinstated.put("cancellationRejection", true);

        when(redisCacheService.get("Cache & Reference Data:cache:claim-123")).thenReturn("PENDING");
        when(dynamoDbService.getItem("Claims & Policy Data Store_table", "claim-123")).thenReturn(Map.of("pk", "claim-123", "status", "PENDING"));
        when(sesCommunicationService.send("admin@newco.com", List.of("claims@newco.com"), "us-east-1")).thenReturn("msg-id-1");

        var decisionNotReinstated = orchestrator.evaluate(payloadNotReinstated);

        // Assert: Cancellation rejections apply when not reinstated
        assertEquals("REJECTED", decisionNotReinstated.get("finalStatus"));
        verify(sesCommunicationService).send(anyString(), anyList(), anyString());

        // Arrange: Claim IS reinstated -> cancellation rejections should be overridden
        Map<String, Object> payloadReinstated = new HashMap<>();
        payloadReinstated.put("id", "claim-123");
        payloadReinstated.put("reinstated", true);
        payloadReinstated.put("cancellationRejection", true);

        var decisionReinstated = orchestrator.evaluate(payloadReinstated);

        // Assert: Cancellation rejections do NOT apply when reinstated
        assertEquals("ACTIVE", decisionReinstated.get("finalStatus"));
        verifyNoInteractions(sesCommunicationService);
    }

    // Mock interfaces mapping to infra I/O contracts
    interface RedisCacheService { String get(String key); }
    interface DynamoDbService { Map<String, Object> getItem(String tableName, String partitionKey); }
    interface SesCommunicationService { String send(String fromAddress, List<String> toAddresses, String region); }

    // Service under test implementing feature orchestration logic
    static class ClaimDecisionOrchestrator {
        private final RedisCacheService redis;
        private final DynamoDbService dynamo;
        private final SesCommunicationService ses;

        ClaimDecisionOrchestrator(RedisCacheService redis, DynamoDbService dynamo, SesCommunicationService ses) {
            this.redis = redis;
            this.dynamo = dynamo;
            this.ses = ses;
        }

        Map<String, Object> evaluate(Map<String, Object> payload) {
            String claimId = (String) payload.get("id");
            boolean isReinstated = (boolean) payload.getOrDefault("reinstated", false);
            boolean hasCancellation = (boolean) payload.getOrDefault("cancellationRejection", false);

            String finalStatus = "PENDING";
            if (hasCancellation && !isReinstated) {
                finalStatus = "REJECTED";
                ses.send("admin@newco.com", List.of("claims@newco.com"), "us-east-1");
            } else if (isReinstated) {
                finalStatus = "ACTIVE";
            }
            return Map.of("id", claimId, "finalStatus", finalStatus);
        }
    }
}
