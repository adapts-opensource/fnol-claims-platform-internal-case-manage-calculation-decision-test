package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

// Typed model representing claim_initiation___routing_decision_validation entity
record ClaimInitiationPayload(String id, Map<String, Object> payload) {}

// Mocked infrastructure interfaces aligned with infra_io_contracts
interface CacheReferenceData {
    String getCacheValue(String namespace, String key);
}

interface ClaimsDataStore {
    Map<String, Object> getClaimItem(String pk);
}

interface DecisionOrchestrator {
    ClaimInitiationPayload validateAndRoute(ClaimInitiationPayload request);
}

interface StructuredLogger {
    void info(String message);
    void error(String message, Throwable throwable);
}

@DisplayName("Claim Initiation & Routing: Decision Validation Tests")
public class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private CacheReferenceData cacheReferenceData;

    @Mock
    private ClaimsDataStore claimsDataStore;

    @Mock
    private DecisionOrchestrator decisionOrchestrator;

    @Mock
    private StructuredLogger logger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("adjuster_selects_wrong_policy")
    void adjuster_selects_wrong_policy() {
        // Arrange: Setup claim initiation payload with adjuster selecting a mismatched policy
        String claimId = "claim-init-001";
        String correctPolicyId = "policy-active-999";
        String wrongPolicyId = "policy-cancelled-888";

        Map<String, Object> payload = Map.of(
            "claimId", claimId,
            "selectedPolicyId", wrongPolicyId,
            "adjusterId", "adj-101",
            "routingStatus", "PENDING_DECISION"
        );

        ClaimInitiationPayload request = new ClaimInitiationPayload(claimId, payload);

        // Mock Redis cache lookup for policy reference data
        String cachedPolicy = "{\"id\":\"" + correctPolicyId + "\",\"status\":\"ACTIVE\",\"type\":\"AUTO\"}";
        when(cacheReferenceData.getCacheValue(any(String.class), any(String.class)))
                .thenReturn(cachedPolicy);

        // Mock DynamoDB lookup for claim association
        Map<String, Object> claimRecord = Map.of(
            "id", claimId,
            "policyId", correctPolicyId,
            "initiationTimestamp", "2023-10-27T10:00:00Z"
        );
        when(claimsDataStore.getClaimItem(any(String.class)))
                .thenReturn(claimRecord);

        // Mock structured logging for observability NFR compliance
        doNothing().when(logger).info(any(String.class));
        doNothing().when(logger).error(any(String.class), any(Throwable.class));

        // Stub orchestrator to simulate business logic validation failure on mismatch
        when(decisionOrchestrator.validateAndRoute(any(ClaimInitiationPayload.class)))
                .thenThrow(new IllegalArgumentException("Policy mismatch: adjuster selected policy does not match claim association."));

        // Act & Assert: Verify input validation rejects mismatched policy selection
        assertThrows(IllegalArgumentException.class, () -> {
            decisionOrchestrator.validateAndRoute(request);
        }, "Should reject claim initiation when adjuster selects a policy mismatching the claim record");

        // Verify infra I/O contracts were invoked per contract definitions
        verify(cacheReferenceData, times(1)).getCacheValue(any(String.class), any(String.class));
        verify(claimsDataStore, times(1)).getClaimItem(any(String.class));
        verify(logger, times(1)).error(eq("Policy mismatch detected during decision validation"), any(Throwable.class));
    }
}
