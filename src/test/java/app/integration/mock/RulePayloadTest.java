package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

public class StateTransitionRulePayloadTest {

    private DataPersistenceClient dataPersistenceClient;

    @BeforeEach
    void setUp() {
        dataPersistenceClient = mock(DataPersistenceClient.class);
    }

    @Test
    void rule_payload() {
        // Arrange
        String claimId = "CLM-123456";
        String currentState = "SUBMITTED";
        String nextState = "UNDER_REVIEW";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("trigger", "INSURED_ACTION");
        metadata.put("timestamp", "2023-10-25T10:00:00Z");

        RulePayload payload = new RulePayload(claimId, currentState, nextState, metadata);

        // Act & Assert: Verify payload structure and constraints
        assertEquals(claimId, payload.getClaimId());
        assertEquals(currentState, payload.getCurrentState());
        assertEquals(nextState, payload.getNextState());
        assertEquals(metadata, payload.getMetadata());
        assertNotNull(payload.toDynamoDbMap());
        assertTrue(payload.toDynamoDbMap().containsKey("claim_id"));

        // Mock external I/O interaction (DynamoDB persistence)
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("status", "SUCCESS");
        expectedResponse.put("item_count", 1);
        when(dataPersistenceClient.saveItem(any(Map.class))).thenReturn(expectedResponse);

        Map<String, Object> result = dataPersistenceClient.saveItem(payload.toDynamoDbMap());

        // Assert mock interaction and response
        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        verify(dataPersistenceClient, times(1)).saveItem(payload.toDynamoDbMap());
    }

    // Minimal payload representation for state transition rule evaluation
    static class RulePayload {
        private final String claimId;
        private final String currentState;
        private final String nextState;
        private final Map<String, Object> metadata;

        public RulePayload(String claimId, String currentState, String nextState, Map<String, Object> metadata) {
            this.claimId = claimId;
            this.currentState = currentState;
            this.nextState = nextState;
            this.metadata = metadata;
        }

        public String getClaimId() { return claimId; }
        public String getCurrentState() { return currentState; }
        public String getNextState() { return nextState; }
        public Map<String, Object> getMetadata() { return metadata; }

        public Map<String, Object> toDynamoDbMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("claim_id", claimId);
            map.put("current_state", currentState);
            map.put("next_state", nextState);
            map.put("metadata", metadata);
            return map;
        }
    }

    // Mocked external I/O contract for Data Persistence_dynamodb
    interface DataPersistenceClient {
        Map<String, Object> saveItem(Map<String, Object> item);
    }
}
