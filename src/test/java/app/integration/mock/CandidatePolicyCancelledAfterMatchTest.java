package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Infrastructure contract stubs for compilation and mocking
interface RedisCacheService { String get(String key); }
interface DynamoDbService { Map<String, Object> getItem(String table, String keyAttr, String keyVal); }
interface SesCommunicationService { String sendEmail(Map<String, Object> params); }

// Service under test for Claim Initiation & Routing:orchestration:decision
class DecisionOrchestratorService {
    private final RedisCacheService redisCache;
    private final DynamoDbService claimsPolicyStore;
    private final SesCommunicationService sesService;

    DecisionOrchestratorService(RedisCacheService redisCache, DynamoDbService claimsPolicyStore, SesCommunicationService sesService) {
        this.redisCache = redisCache;
        this.claimsPolicyStore = claimsPolicyStore;
        this.sesService = sesService;
    }

    Map<String, Object> evaluateCandidatePolicy(Map<String, Object> payload) {
        String policyId = (String) ((Map<String, Object>) payload.get("payload")).get("policyId");
        
        // Cache lookup
        redisCache.get("Cache & Reference Data:cache:policy:" + policyId);
        
        // Policy data retrieval
        Map<String, Object> policyData = claimsPolicyStore.getItem("Claims & Policy Data Store_table", "pk", policyId);
        String status = (String) policyData.get("status");

        Map<String, Object> result = new java.util.HashMap<>();
        if ("CANCELLED".equals(status)) {
            result.put("routingStatus", "REJECTED");
            result.put("rejectionReason", "POLICY_CANCELLED");
            result.put("requiresManualReview", true);
        } else {
            result.put("routingStatus", "APPROVED");
            result.put("requiresManualReview", false);
        }
        return result;
    }
}

/**
 * Validates Claim Initiation & Routing:orchestration:decision
 * specifically handling the scenario where a candidate policy is cancelled after matching.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private RedisCacheService redisCache;

    @Mock
    private DynamoDbService claimsPolicyStore;

    @Mock
    private SesCommunicationService sesService;

    private DecisionOrchestratorService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new DecisionOrchestratorService(redisCache, claimsPolicyStore, sesService);
    }

    @Test
    void candidate_policy_cancelled_after_match() {
        // Arrange
        String claimId = "CLM-1001";
        String candidatePolicyId = "POL-9001";
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "payload", Map.of(
                "policyId", candidatePolicyId,
                "claimType", "AUTO",
                "incidentDate", "2024-05-15"
            )
        );

        // Mock Redis cache hit for policy reference
        when(redisCache.get("Cache & Reference Data:cache:policy:" + candidatePolicyId))
                .thenReturn(candidatePolicyId);

        // Mock DynamoDB fetch returning CANCELLED status
        Map<String, Object> policyItem = Map.of(
                "pk", candidatePolicyId,
                "status", "CANCELLED",
                "coverageType", "COMPREHENSIVE"
        );
        when(claimsPolicyStore.getItem("Claims & Policy Data Store_table", "pk", candidatePolicyId))
                .thenReturn(policyItem);

        // Act
        Map<String, Object> decisionResult = decisionService.evaluateCandidatePolicy(payload);

        // Assert
        assertNotNull(decisionResult, "Decision result must not be null");
        assertEquals("REJECTED", decisionResult.get("routingStatus"), "Should reject due to policy status");
        assertEquals("POLICY_CANCELLED", decisionResult.get("rejectionReason"));
        assertTrue((Boolean) decisionResult.get("requiresManualReview"), "Should flag for manual review");

        // Verify infra I/O contracts
        verify(redisCache, times(1)).get(anyString());
        verify(claimsPolicyStore, times(1)).getItem(eq("Claims & Policy Data Store_table"), eq("pk"), eq(candidatePolicyId));
        verify(sesService, never()).sendEmail(anyMap());
    }
}
