package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PolicyDbUnreachableFallbackToCachedSnapshotWithStaleFlagTest {

    @Mock
    private PolicyDbClient policyDbClient;

    @Mock
    private CacheClient cacheClient;

    @InjectMocks
    private DecisionCalculationService decisionCalculationService;

    private static final String CLAIM_ID = "claim-123";
    private static final String CACHE_KEY_PREFIX = "Cache & Reference Data:cache:";
    private static final Map<String, Object> STALE_SNAPSHOT = Map.of(
        "policyId", "POL-456",
        "coverageType", "AUTO",
        "stale", true
    );

    @BeforeEach
    void setUp() {
        // MockitoExtension handles field injection automatically.
        // Thread-safety is guaranteed per test instance by JUnit 5's default lifecycle.
    }

    @Test
    @DisplayName("policy_db_unreachable_fallback_to_cached_snapshot_with_stale_flag")
    void policyDbUnreachableFallbackToCachedSnapshotWithStaleFlag() {
        // Given: Policy DB is unreachable
        when(policyDbClient.fetchPolicyData(anyString()))
            .thenThrow(new RuntimeException("DynamoDB connection refused"));

        // Given: Cache contains a stale snapshot
        String cacheKey = CACHE_KEY_PREFIX + CLAIM_ID;
        when(cacheClient.get(cacheKey)).thenReturn(STALE_SNAPSHOT);

        // When: Decision calculation is triggered
        Map<String, Object> result = decisionCalculationService.calculateClaimDecision(CLAIM_ID);

        // Then: System falls back to cache, preserves stale flag, and does not throw
        assertNotNull(result, "Result should not be null when fallback occurs");
        assertEquals("AUTO", result.get("coverageType"), "Coverage type should match cached data");
        assertTrue((Boolean) result.get("stale"), "Stale flag should be true");

        // Verify interactions: DB attempted, Cache read
        verify(policyDbClient, times(1)).fetchPolicyData(CLAIM_ID);
        verify(cacheClient, times(1)).get(cacheKey);
        verifyNoMoreInteractions(policyDbClient, cacheClient);
    }

    // Minimal interfaces for test isolation
    interface PolicyDbClient {
        Map<String, Object> fetchPolicyData(String claimId);
    }

    interface CacheClient {
        Map<String, Object> get(String cacheKey);
    }

    // Service under test
    static class DecisionCalculationService {
        private final PolicyDbClient policyDbClient;
        private final CacheClient cacheClient;

        DecisionCalculationService(PolicyDbClient policyDbClient, CacheClient cacheClient) {
            this.policyDbClient = policyDbClient;
            this.cacheClient = cacheClient;
        }

        Map<String, Object> calculateClaimDecision(String claimId) {
            try {
                return policyDbClient.fetchPolicyData(claimId);
            } catch (Exception e) {
                // Fallback to cached snapshot with stale flag
                Map<String, Object> cached = cacheClient.get(CACHE_KEY_PREFIX + claimId);
                if (cached != null) {
                    cached.put("stale", true);
                    return cached;
                }
                throw new RuntimeException("Fallback failed: DB unreachable and cache empty");
            }
        }
    }
}
