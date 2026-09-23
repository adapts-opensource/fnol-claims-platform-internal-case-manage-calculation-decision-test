package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Mocked infrastructure clients per infra_io_contracts
interface RedisClient {
    void setCacheKeyNamespace(String cacheKeyNamespace, Map<String, Object> cachedValue, int ttlSeconds);
}

interface DynamoDbClient {
    void putItem(String tableName, String partitionKey, Map<String, Object> itemPayload);
}

// Service under test for Claim Initiation & Routing:decision:calculation
class ClaimRoutingDecisionService {
    private final RedisClient redisClient;
    private final DynamoDbClient dynamoDbClient;

    ClaimRoutingDecisionService(RedisClient redisClient, DynamoDbClient dynamoDbClient) {
        this.redisClient = redisClient;
        this.dynamoDbClient = dynamoDbClient;
    }

    boolean calculateAndTrackDeadlines(String claimId, Map<String, Object> payload) {
        // Simulate calculation & creation of deadline tasks
        String cacheKey = "Cache & Reference Data:cache:deadline:" + claimId;
        Map<String, Object> cachedValue = Map.of(
                "claimId", claimId,
                "tasksCreated", true,
                "trackable", true,
                "status", "ACTIVE"
        );
        redisClient.setCacheKeyNamespace(cacheKey, cachedValue, 3600);

        String tableName = "Claims & Policy Data Store_table";
        Map<String, Object> itemPayload = Map.of("id", claimId, "payload", payload);
        dynamoDbClient.putItem(tableName, "pk", itemPayload);

        return true;
    }
}

public class DeadlineTasksCreatedAndTrackableTest {

    @Mock
    private RedisClient redisClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    private ClaimRoutingDecisionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ClaimRoutingDecisionService(redisClient, dynamoDbClient);
    }

    @Test
    void deadline_tasks_created_and_trackable() {
        String claimId = "CLM-78901";
        Map<String, Object> payload = Map.of(
                "policyNumber", "POL-54321",
                "incidentDate", "2024-05-12",
                "coverageType", "AUTO"
        );

        boolean result = service.calculateAndTrackDeadlines(claimId, payload);

        assertTrue(result, "Decision calculation should return true when deadlines are created and trackable");

        // Verify Redis cache interaction (Cache & Reference Data_elasticache)
        ArgumentCaptor<String> cacheKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> cachedValueCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Integer> ttlCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(redisClient).setCacheKeyNamespace(
                cacheKeyCaptor.capture(),
                cachedValueCaptor.capture(),
                ttlCaptor.capture()
        );

        assertEquals("Cache & Reference Data:cache:deadline:" + claimId, cacheKeyCaptor.getValue());
        assertTrue(cachedValueCaptor.getValue().containsKey("tasksCreated"));
        assertTrue(cachedValueCaptor.getValue().containsKey("trackable"));
        assertEquals(3600, ttlCaptor.getValue());

        // Verify DynamoDB interaction (Claims & Policy Data Store_dynamodb)
        ArgumentCaptor<String> tableCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> pkCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        verify(dynamoDbClient).putItem(
                tableCaptor.capture(),
                pkCaptor.capture(),
                payloadCaptor.capture()
        );

        assertEquals("Claims & Policy Data Store_table", tableCaptor.getValue());
        assertEquals("pk", pkCaptor.getValue());
        assertEquals(claimId, payloadCaptor.getValue().get("id"));
        assertEquals(payload, payloadCaptor.getValue().get("payload"));
    }
}
