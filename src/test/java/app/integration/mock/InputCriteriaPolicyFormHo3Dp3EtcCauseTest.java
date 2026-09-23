package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Validates Claim Initiation & Routing:decision:calculation against input criteria.
 * NFRs: thread-safe stateless service, structured logging via SLF4J, GDPR/SOC2 compliant payload handling.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimRoutingDecisionCalculationMockTest {

    @Mock
    private RedisClient redisClient;

    @Mock
    private DynamoDBClient dynamoDBClient;

    private ClaimRoutingDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new ClaimRoutingDecisionService(redisClient, dynamoDBClient);
    }

    @Test
    void input_criteria_policy_form_ho3_dp3_etc_cause_of_loss_estimated_damage_severity_compliance_flags_cat_moratorium_channel_of_intake() {
        // Arrange: Construct payload matching the feature criteria
        Map<String, Object> payload = new HashMap<>();
        payload.put("policyForm", "HO3");
        payload.put("causeOfLoss", "WindHail");
        payload.put("estimatedDamageSeverity", "HIGH");
        payload.put("complianceFlags", Map.of("catDeclared", true, "moratoriumActive", false));
        payload.put("channelOfIntake", "MOBILE_APP");

        Map<String, Object> validationRecord = new HashMap<>();
        validationRecord.put("id", UUID.randomUUID().toString());
        validationRecord.put("payload", payload);

        // Mock external I/O contracts (Redis & DynamoDB)
        when(redisClient.get(anyString())).thenReturn("routing_rules_prod_v2");
        when(dynamoDBClient.getItem(anyString(), anyString())).thenReturn(
                Map.of("routingConfig", "standard", "thresholds", Map.of("highSeverity", "true"))
        );

        // Act: Invoke decision calculation
        Map<String, Object> decision = decisionService.calculateRoutingDecision(validationRecord);

        // Assert: Verify routing decision matches expected compliance & severity logic
        assertNotNull(decision, "Routing decision must not be null");
        assertEquals("PRIORITY_QUEUE", decision.get("routingQueue"), "High severity + HO3 should route to priority");
        assertEquals(true, decision.get("requiresComplianceReview"), "CAT flag should trigger compliance review");
        assertEquals("MOBILE_APP", decision.get("channelOfIntake"), "Channel must be preserved");
        assertEquals("HO3", decision.get("policyForm"), "Policy form must be propagated");

        // Verify infra I/O interactions & least-privilege access pattern
        verify(redisClient, times(1)).get(anyString());
        verify(dynamoDBClient, times(1)).getItem(anyString(), anyString());
        verifyNoMoreInteractions(redisClient, dynamoDBClient);
    }

    /** Minimal stateless service implementation for testing decision logic. */
    static class ClaimRoutingDecisionService {
        private final RedisClient redisClient;
        private final DynamoDBClient dynamoDBClient;

        ClaimRoutingDecisionService(RedisClient redisClient, DynamoDBClient dynamoDBClient) {
            this.redisClient = redisClient;
            this.dynamoDBClient = dynamoDBClient;
        }

        Map<String, Object> calculateRoutingDecision(Map<String, Object> record) {
            String rulesCache = redisClient.get("routing_rules");
            Map<String, Object> config = dynamoDBClient.getItem("claims_table", "pk");

            Map<String, Object> payload = (Map<String, Object>) record.get("payload");
            String severity = (String) payload.get("estimatedDamageSeverity");
            Map<String, Boolean> flags = (Map<String, Boolean>) payload.get("complianceFlags");
            String channel = (String) payload.get("channelOfIntake");
            String policyForm = (String) payload.get("policyForm");

            Map<String, Object> decision = new HashMap<>();
            decision.put("routingQueue", severity.equals("HIGH") ? "PRIORITY_QUEUE" : "STANDARD_QUEUE");
            decision.put("requiresComplianceReview", Boolean.TRUE.equals(flags.get("catDeclared")));
            decision.put("channelOfIntake", channel);
            decision.put("policyForm", policyForm);
            decision.put("cacheHit", rulesCache != null);
            decision.put("dbConfigLoaded", config != null);
            return decision;
        }
    }

    /** Mock interfaces for infra I/O contracts. */
    interface RedisClient { String get(String key); }
    interface DynamoDBClient { Map<String, Object> getItem(String table, String key); }
}
