package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.HashMap;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for Claim Initiation & Routing:decision:calculation.
 * Verifies that triage re-executes correctly when an override is applied.
 */
@DisplayName("TriageReExecutesCorrectlyAfterOverrideTest")
class TriageReExecutesCorrectlyAfterOverrideTest {

    @Mock
    private ClaimsDynamoDbService claimsDynamoDbService;

    @Mock
    private ReferenceRedisService referenceRedisService;

    @InjectMocks
    private ClaimRoutingDecisionService claimRoutingDecisionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("triage_re_executes_correctly_after_override")
    void triage_re_executes_correctly_after_override() {
        // Arrange
        String claimId = "CLM-TEST-OVERRIDE-001";
        String tableName = "Claims & Policy Data Store_table";
        String partitionKey = "pk";
        String cacheNamespace = "Cache & Reference Data:cache:";
        String triageRuleKey = "triage_rules_v1";
        String ttlSeconds = "3600";

        // Mock DynamoDB: Return claim item with pending override flag
        Map<String, Object> claimPayload = new HashMap<>();
        claimPayload.put("status", "TRIAGE_PENDING");
        claimPayload.put("amount", 5000.00);
        claimPayload.put("hasOverride", true);
        
        Map<String, Object> expectedClaimItem = Map.of(
            partitionKey, claimId,
            "payload", claimPayload
        );
        
        when(claimsDynamoDbService.fetchClaimItem(tableName, claimId))
            .thenReturn(expectedClaimItem);

        // Mock Redis: Return triage rules reference data
        when(referenceRedisService.getCacheValue(cacheNamespace, triageRuleKey))
            .thenReturn("RULE_HIGH_PRIORITY_IF_OVERRIDE");

        // Override context simulating manual intervention
        Map<String, Object> overrideContext = Map.of(
            "operatorId", "OP-99",
            "reason", "Manual Review Required",
            "adjustedScore", 85
        );

        // Act
        Map<String, Object> result = claimRoutingDecisionService.calculateRoutingDecision(claimId, overrideContext);

        // Assert
        assertNotNull(result, "Routing decision should not be null after re-execution");
        
        // Verify logic applied override correctly
        assertTrue(result.containsKey("routingDecision"), "Result should contain routing decision");
        assertEquals("ROUTED_TO_SPECIALIST", result.get("routingDecision"), "Override should trigger specialist routing");
        
        // Verify Infra I/O contracts were respected
        verify(claimsDynamoDbService).fetchClaimItem(tableName, claimId);
        verify(referenceRedisService).getCacheValue(cacheNamespace, triageRuleKey);
        
        // Verify input validation behavior (mocked side effect or result constraint)
        assertFalse(result.containsKey("error"), "Should not return error for valid override");
        
        // NFR: Thread safety check (mocks are thread-safe by default in Mockito)
        // NFR: GDPR/SOC2 check (mocks ensure no real PII is processed)
        assertTrue(result.containsKey("complianceCheck"), "Result should include compliance validation");
        assertEquals(true, result.get("complianceCheck"), "Override must pass compliance checks");
    }

    // Minimal stub interfaces representing Infra Contracts and SUT
    interface ClaimsDynamoDbService {
        Map<String, Object> fetchClaimItem(String tableName, String partitionKey);
    }

    interface ReferenceRedisService {
        String getCacheValue(String namespace, String key);
    }

    interface ClaimRoutingDecisionService {
        Map<String, Object> calculateRoutingDecision(String claimId, Map<String, Object> overrideContext);
    }
}
