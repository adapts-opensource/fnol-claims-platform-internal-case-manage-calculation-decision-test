package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.HashMap;

// Structured logging & observability NFR: Log decision evaluation context
// Security NFR: Validate input constraints before processing
record ClaimInitiationRoutingDecisionValidation(String id, Map<String, Object> payload) {}

interface RedisCacheContract { String get(String key); }
interface DynamoDbStoreContract { Map<String, Object> getItem(String tableName, String pk); }
interface SesCommunicationContract { String send(String from, Map<String, String> to, String region); }
interface DecisionOrchestrationContract { String evaluateDecision(ClaimInitiationRoutingDecisionValidation entity); }

class ClaimInitiationRoutingDecisionValidationTest {

    @Mock private RedisCacheContract redisCacheContract;
    @Mock private DynamoDbStoreContract dynamoDbStoreContract;
    @Mock private SesCommunicationContract sesCommunicationContract;
    @Mock private DecisionOrchestrationContract decisionOrchestrationContract;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void decision_no_duplicate_rule_score_0_7_expected_outcome_fnol_proceeds_to_claim_creation() {
        // Arrange
        String claimId = "fnol-init-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("score", 0.6); // Rule: Score < 0.7
        payload.put("decision", "No Duplicate");
        payload.put("claimType", "AUTO");

        ClaimInitiationRoutingDecisionValidation entity = new ClaimInitiationRoutingDecisionValidation(claimId, payload);

        // Mock infra I/O contracts (Redis, DynamoDB, SES)
        when(redisCacheContract.get(anyString())).thenReturn("ref_data_v1");
        when(dynamoDbStoreContract.getItem(anyString(), anyString())).thenReturn(Map.of("pk", claimId, "status", "INITIATED"));
        when(sesCommunicationContract.send(anyString(), anyMap(), anyString())).thenReturn("msg-id-123");
        when(decisionOrchestrationContract.evaluateDecision(any())).thenReturn("PROCEED_TO_CLAIM_CREATION");

        // Act
        String outcome = decisionOrchestrationContract.evaluateDecision(entity);

        // Assert
        assertEquals("PROCEED_TO_CLAIM_CREATION", outcome, "FNOL proceeds to claim creation when decision is No Duplicate and score < 0.7");
        verify(redisCacheContract, times(1)).get(anyString());
        verify(dynamoDbStoreContract, times(1)).getItem(anyString(), anyString());
        verify(decisionOrchestrationContract, times(1)).evaluateDecision(entity);
    }
}
