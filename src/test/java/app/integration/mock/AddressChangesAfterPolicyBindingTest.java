package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

record ClaimInitiationValidation(String id, Map<String, Object> payload) {}

class AddressChangesAfterPolicyBindingTest {

    @Mock
    private RedisCacheService cacheService;

    @Mock
    private DynamoDbPolicyStore policyStore;

    @Mock
    private SesNotificationService notificationService;

    @Mock
    private ClaimRoutingDecisionEngine decisionEngine;

    private String claimId;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        claimId = "CLM-INIT-7890";
        payload = new HashMap<>();
        payload.put("policyBindingAddress", "100 Main St, Springfield, IL, 62701");
        payload.put("claimantResidentialAddress", "200 Oak Ave, Springfield, IL, 62702");
        payload.put("claimType", "AUTO_COLLISION");
        payload.put("submittedTimestamp", "2023-11-15T09:30:00Z");
    }

    @Test
    void addressChangesAfterPolicyBinding() {
        // Arrange
        ClaimInitiationValidation validation = new ClaimInitiationValidation(claimId, payload);
        when(cacheService.get("Cache & Reference Data:cache:" + claimId)).thenReturn(null);
        when(policyStore.getItem("Claims & Policy Data Store_table", "pk:" + claimId)).thenReturn(Map.of(
            "policyId", "POL-5544",
            "bindingAddress", "100 Main St, Springfield, IL, 62701",
            "status", "ACTIVE"
        ));

        // Act
        Map<String, Object> routingDecision = decisionEngine.evaluateClaimInitiation(validation);

        // Assert
        assertNotNull(routingDecision, "Routing decision must not be null");
        assertEquals("SPECIALIZED_ADDRESS_DISCREPANCY_QUEUE", routingDecision.get("routingTarget"));
        assertTrue((boolean) routingDecision.get("addressMismatchDetected"));
        assertEquals(claimId, routingDecision.get("claimId"));

        // Verify infra I/O contracts
        verify(cacheService, times(1)).get("Cache & Reference Data:cache:" + claimId);
        verify(policyStore, times(1)).getItem("Claims & Policy Data Store_table", "pk:" + claimId);
        verify(notificationService, never()).sendMessage(anyString(), anyList());
    }
}

interface RedisCacheService { String get(String key); }
interface DynamoDbPolicyStore { Map<String, Object> getItem(String tableName, String partitionKey); }
interface SesNotificationService { String sendMessage(String fromAddress, List<String> toAddresses); }
interface ClaimRoutingDecisionEngine { Map<String, Object> evaluateClaimInitiation(ClaimInitiationValidation validation); }
