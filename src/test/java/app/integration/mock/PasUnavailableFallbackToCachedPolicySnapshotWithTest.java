package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PasUnavailableFallbackToCachedPolicySnapshotWithStaleFlagTest {

    @Mock
    private PolicyAccessService pasService;

    @Mock
    private CacheStorageService cacheService;

    @Mock
    private ClaimDataStoreService dataStore;

    private ClaimDataStandardizationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimDataStandardizationOrchestrator(pasService, cacheService, dataStore);
    }

    @Test
    void pas_unavailable_fallback_to_cached_policy_snapshot_with_stale_flag() {
        // Given
        String claimId = "CLM-789";
        ServiceUnavailableException pasException = new ServiceUnavailableException("PAS unavailable");
        Map<String, Object> cachedSnapshot = new HashMap<>();
        cachedSnapshot.put("policyNumber", "POL-456");
        cachedSnapshot.put("coverageType", "AUTO");
        cachedSnapshot.put("stale", true);

        when(pasService.fetchPolicySnapshot(claimId)).thenThrow(pasException);
        when(cacheService.getCachedSnapshot(claimId)).thenReturn(cachedSnapshot);

        // When
        Map<String, Object> resultPayload = orchestrator.processStateTransition(claimId);

        // Then
        assertNotNull(resultPayload, "Payload should not be null after fallback");
        assertEquals("POL-456", resultPayload.get("policyNumber"));
        assertEquals("AUTO", resultPayload.get("coverageType"));
        assertTrue((Boolean) resultPayload.get("stale"), "Snapshot should retain stale flag");
        assertEquals("CACHED", resultPayload.get("snapshotSource"), "Should indicate cached fallback");

        verify(pasService).fetchPolicySnapshot(claimId);
        verify(cacheService).getCachedSnapshot(claimId);
        verifyNoInteractions(dataStore);
    }

    // Supporting types for isolated mock execution
    static class ServiceUnavailableException extends RuntimeException {
        ServiceUnavailableException(String message) { super(message); }
    }

    interface PolicyAccessService {
        Map<String, Object> fetchPolicySnapshot(String claimId);
    }

    interface CacheStorageService {
        Map<String, Object> getCachedSnapshot(String claimId);
    }

    interface ClaimDataStoreService {
        void savePayload(String claimId, Map<String, Object> payload);
    }

    static class ClaimDataStandardizationOrchestrator {
        private final PolicyAccessService pasService;
        private final CacheStorageService cacheService;
        private final ClaimDataStoreService dataStore;

        ClaimDataStandardizationOrchestrator(PolicyAccessService pasService, CacheStorageService cacheService, ClaimDataStoreService dataStore) {
            this.pasService = pasService;
            this.cacheService = cacheService;
            this.dataStore = dataStore;
        }

        Map<String, Object> processStateTransition(String claimId) {
            try {
                return pasService.fetchPolicySnapshot(claimId);
            } catch (ServiceUnavailableException e) {
                Map<String, Object> cached = cacheService.getCachedSnapshot(claimId);
                cached.put("snapshotSource", "CACHED");
                return cached;
            }
        }
    }
}
