package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Infrastructure Interfaces (Mocked in Tests)
interface RedisClient {
    String get(String key);
}

interface DynamoDbClient {
    Map<String, Object> getItem(String tableName, String partitionKey);
}

interface EmailService {
    String sendNotification(String fromAddress, String toAddress, String subject, String body);
}

// System Under Test
class ClaimDecisionOrchestrator {
    private final RedisClient redis;
    private final DynamoDbClient dynamoDb;
    private final EmailService email;

    ClaimDecisionOrchestrator(RedisClient redis, DynamoDbClient dynamoDb, EmailService email) {
        this.redis = redis;
        this.dynamoDb = dynamoDb;
        this.email = email;
    }

    Map<String, Object> evaluateClaimInitiation(String claimId, String policyId) {
        String cachedPolicy = redis.get("Cache & Reference Data:cache:" + policyId);
        Map<String, Object> policyData = parseJsonToMap(cachedPolicy);
        Map<String, Object> lossData = dynamoDb.getItem("Claims & Policy Data Store_table", policyId);

        boolean isGracePeriodActive = Boolean.parseBoolean(policyData.get("isGracePeriodActive").toString());
        Instant lossDate = Instant.parse(lossData.get("lossDate").toString());
        Instant graceExpiry = Instant.parse(policyData.get("gracePeriodExpiry").toString());

        boolean lossWithinGrace = !lossDate.isAfter(graceExpiry);
        boolean shouldRouteStandard = isGracePeriodActive && lossWithinGrace;

        return Map.of(
            "id", claimId,
            "payload", Map.of(
                "routingDecision", shouldRouteStandard ? "ROUTED_TO_STANDARD_CLAIMS_QUEUE" : "ROUTED_TO_SUSPICIOUS_ACTIVITY_QUEUE",
                "allowGracePeriodProcessing", shouldRouteStandard,
                "requiresImmediateReview", false
            )
        );
    }

    private Map<String, Object> parseJsonToMap(String json) {
        Map<String, Object> map = new java.util.HashMap<>();
        if (json == null) return map;
        String clean = json.replace("{", "").replace("}", "").replace("\"", "").replace(",", ";");
        for (String pair : clean.split(";")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
        }
        return map;
    }
}

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private RedisClient redisClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private EmailService emailService;

    private ClaimDecisionOrchestrator orchestrator;

    private static final String CACHE_KEY_NAMESPACE = "Cache & Reference Data:cache:";
    private static final String POLICY_ID = "POL-123456";
    private static final String CLAIM_ID = "CLM-789012";

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimDecisionOrchestrator(redisClient, dynamoDbClient, emailService);
    }

    @Test
    void policyExpiredButLossOccurredDuringExpirationGracePeriod() {
        // Arrange
        String cacheKey = CACHE_KEY_NAMESPACE + POLICY_ID;
        Instant lossDate = Instant.now().minusSeconds(86400); // 1 day ago
        Instant policyExpiry = Instant.now().minusSeconds(172800); // 2 days ago
        long gracePeriodSeconds = 259200; // 3 days
        Instant graceExpiry = Instant.now().plusSeconds(gracePeriodSeconds);

        String policyStatusJson = """
            {
              "policyId": "%s",
              "status": "EXPIRED",
              "expiryDate": "%s",
              "gracePeriodExpiry": "%s",
              "isGracePeriodActive": "true"
            }
            """.formatted(POLICY_ID, policyExpiry.toString(), graceExpiry.toString());

        when(redisClient.get(cacheKey)).thenReturn(policyStatusJson);

        Map<String, Object> lossPayload = Map.of(
            "claimId", CLAIM_ID,
            "lossDate", lossDate.toString(),
            "policyId", POLICY_ID
        );
        when(dynamoDbClient.getItem("Claims & Policy Data Store_table", POLICY_ID)).thenReturn(lossPayload);

        // Act
        Map<String, Object> decision = orchestrator.evaluateClaimInitiation(CLAIM_ID, POLICY_ID);

        // Assert
        assertNotNull(decision);
        assertEquals(CLAIM_ID, decision.get("id"));
        
        Map<String, Object> payload = (Map<String, Object>) decision.get("payload");
        assertEquals("ROUTED_TO_STANDARD_CLAIMS_QUEUE", payload.get("routingDecision"));
        assertTrue((Boolean) payload.get("allowGracePeriodProcessing"));
        assertFalse((Boolean) payload.get("requiresImmediateReview"));

        verify(redisClient, times(1)).get(cacheKey);
        verify(dynamoDbClient, times(1)).getItem("Claims & Policy Data Store_table", POLICY_ID);
        verifyNoInteractions(emailService);
    }
}
