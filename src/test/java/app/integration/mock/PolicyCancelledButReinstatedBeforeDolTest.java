package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionMockTest {

    @Mock
    private RedisCacheService redisCacheService;
    @Mock
    private DynamoDbPolicyStore dynamoDbPolicyStore;
    @Mock
    private SesCommunicationService sesCommunicationService;
    @Mock
    private DecisionOrchestrator decisionOrchestrator;

    private Map<String, Object> payload;
    private String entityId;

    @BeforeEach
    void setUp() {
        entityId = "claim-init-routing-decision-001";
        payload = Map.of(
            "policyId", "POL-98765",
            "dateOfLoss", "2024-11-15",
            "reinstatementDate", "2024-10-01",
            "previousStatus", "CANCELLED",
            "currentStatus", "ACTIVE",
            "claimInitiationTimestamp", "2024-10-10T14:30:00Z"
        );
    }

    @Test
    void policy_cancelled_but_reinstated_before_dol() {
        // Arrange
        String cacheKey = "Cache & Reference Data:cache:POL-98765";
        String policyTable = "Claims & Policy Data Store_table";
        String partitionKey = "pk";

        // Simulate cache miss, triggering authoritative DB lookup
        when(redisCacheService.get(anyString())).thenReturn(null);
        when(dynamoDbPolicyStore.getItemByPartitionKey(eq(policyTable), eq(partitionKey), eq("POL-98765")))
                .thenReturn(Map.of(
                    "pk", "POL-98765",
                    "status", "ACTIVE",
                    "reinstatementDate", "2024-10-01",
                    "lastCancellationDate", "2024-09-15"
                ));

        // Mock the orchestration decision logic
        when(decisionOrchestrator.evaluateDecision(any(Map.class))).thenReturn(DecisionOutcome.ROUTE_TO_UNDERWRITING);

        // Act
        DecisionOutcome outcome = decisionOrchestrator.evaluateDecision(payload);

        // Assert
        assertEquals(DecisionOutcome.ROUTE_TO_UNDERWRITING, outcome);
        verify(redisCacheService).get(eq(cacheKey));
        verify(dynamoDbPolicyStore).getItemByPartitionKey(eq(policyTable), eq(partitionKey), eq("POL-98765"));
        verifyNoInteractions(sesCommunicationService);
    }

    // Infrastructure I/O Contract Mocks
    private interface RedisCacheService { String get(String key); }
    private interface DynamoDbPolicyStore { Map<String, Object> getItemByPartitionKey(String table, String pk, String keyValue); }
    private interface SesCommunicationService { String sendMessage(String from, List<String> to, String region); }
    private interface DecisionOrchestrator { DecisionOutcome evaluateDecision(Map<String, Object> payload); }
}
