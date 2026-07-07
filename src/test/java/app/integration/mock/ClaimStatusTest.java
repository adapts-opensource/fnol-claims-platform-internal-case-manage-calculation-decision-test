package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 test class for Claim Initiation & Routing:decision:calculation feature.
 * Verifies claim status calculation logic using mocked external I/O.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbPolicyStore dynamoDbPolicyStore;

    @InjectMocks
    private ClaimDecisionCalculationEngine claimDecisionCalculationEngine;

    private static final String CACHE_KEY_NAMESPACE = "Cache & Reference Data:cache:";
    private static final String TABLE_NAME = "Claims & Policy Data Store_table";
    private static final String PARTITION_KEY = "pk";

    @BeforeEach
    void setUp() {
        // Reset mocks if necessary for isolation, though MockitoExtension handles this by default
    }

    /**
     * Test Case: ClaimStatus
     * Description: Verifies that claim status is correctly calculated based on payload and reference data.
     */
    @Test
    void claimStatus() {
        // Arrange
        String claimId = "claim-uuid-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("claimType", "AUTO");
        payload.put("damageAmount", 500.00);
        payload.put("initiationChannel", "WEB");

        String cacheKey = CACHE_KEY_NAMESPACE + "routing_rules";
        when(redisCacheService.get(cacheKey)).thenReturn("AUTO_STANDARD_ROUTING");

        Map<String, Object> policyItem = Map.of(PARTITION_KEY, claimId, "status", "ACTIVE", "coverageType", "FULL");
        when(dynamoDbPolicyStore.getItem(TABLE_NAME, claimId)).thenReturn(policyItem);

        // Act
        ClaimStatus resultStatus = claimDecisionCalculationEngine.calculateClaimStatus(payload);

        // Assert
        assertNotNull(resultStatus, "Claim status should not be null");
        assertEquals(ClaimStatus.OPEN, resultStatus, "Claim status should be OPEN for valid initiation");

        // Verify external I/O contracts
        verify(redisCacheService, times(1)).get(cacheKey);
        verify(dynamoDbPolicyStore, times(1)).getItem(TABLE_NAME, claimId);
    }

    /**
     * Test Case: ClaimStatusWithInvalidPayload
     * Description: Verifies input validation throws exception for missing required fields.
     */
    @Test
    void claimStatusWithInvalidPayload() {
        // Arrange
        Map<String, Object> invalidPayload = new HashMap<>();
        invalidPayload.put("claimType", "AUTO"); // Missing 'id'

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            claimDecisionCalculationEngine.calculateClaimStatus(invalidPayload);
        }, "Should throw exception for missing required payload field 'id'");
    }

    /**
     * Test Case: ClaimStatusCacheMiss
     * Description: Verifies behavior when reference data is missing from cache.
     */
    @Test
    void claimStatusCacheMiss() {
        // Arrange
        String claimId = "claim-uuid-002";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("claimType", "AUTO");

        String cacheKey = CACHE_KEY_NAMESPACE + "routing_rules";
        when(redisCacheService.get(cacheKey)).thenReturn(null);

        Map<String, Object> policyItem = Map.of(PARTITION_KEY, claimId, "status", "ACTIVE");
        when(dynamoDbPolicyStore.getItem(TABLE_NAME, claimId)).thenReturn(policyItem);

        // Act
        ClaimStatus resultStatus = claimDecisionCalculationEngine.calculateClaimStatus(payload);

        // Assert
        assertEquals(ClaimStatus.PENDING_ROUTING, resultStatus, "Status should be PENDING_ROUTING when cache miss occurs");
        verify(redisCacheService, times(1)).get(cacheKey);
    }
}
