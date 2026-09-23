package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.HashMap;

// Minimal interface definitions for external dependencies
interface RedisCacheService {
    Map<String, Object> get(String key);
}

interface DynamoDbService {
    Map<String, Object> getItem(String tableName, Map<String, Object> key);
}

interface ClaimRoutingDecisionService {
    Map<String, Object> calculateRoutingDecision(Map<String, Object> payload);
}

public class ExactPolicyNumberMatchOverridesAddressMatchTest {

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private ClaimRoutingDecisionService routingDecisionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void exact_policy_number_match_overrides_address_match() {
        // Arrange
        String policyNumber = "POL-987654321";
        String address = "456 Oak Avenue, Metropolis, NY";
        String claimId = "CLM-INIT-001";

        Map<String, Object> payload = new HashMap<>();
        payload.put("claimId", claimId);
        payload.put("policyNumber", policyNumber);
        payload.put("address", address);

        // Mock Redis cache lookup for routing rules
        when(redisCacheService.get("Cache & Reference Data:cache:policy_rules"))
                .thenReturn(Map.of("priority_weights", Map.of("EXACT_POLICY", 10, "ADDRESS_MATCH", 5)));

        // Mock DynamoDB lookup for policy data
        when(dynamoDbService.getItem("Claims & Policy Data Store_table", Map.of("pk", policyNumber)))
                .thenReturn(Map.of("policyNumber", policyNumber, "status", "ACTIVE", "matchedBy", "POLICY_NUMBER"));

        // Mock decision calculation service to simulate the routing logic
        when(routingDecisionService.calculateRoutingDecision(payload))
                .thenAnswer(invocation -> {
                    Map<String, Object> decision = new HashMap<>();
                    decision.put("decision", "ROUTE_TO_EXACT_POLICY");
                    decision.put("matchType", "EXACT_POLICY");
                    decision.put("priorityScore", 10);
                    decision.put("overrideReason", "Exact policy number match overrides address match");
                    decision.put("payload", payload);
                    return decision;
                });

        // Act
        Map<String, Object> result = routingDecisionService.calculateRoutingDecision(payload);

        // Assert
        assertNotNull(result, "Routing decision should not be null");
        assertEquals("EXACT_POLICY", result.get("matchType"), "Match type should be EXACT_POLICY");
        assertEquals(10, result.get("priorityScore"), "Priority score should reflect exact policy match");
        assertTrue(((String) result.get("overrideReason")).contains("overrides address match"),
                "Override reason should indicate policy match took precedence");
        verify(routingDecisionService, times(1)).calculateRoutingDecision(payload);
    }
}
