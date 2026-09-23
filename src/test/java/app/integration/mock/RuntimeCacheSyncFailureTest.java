package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionRuntimeCacheSyncFailureTest {

    @Mock
    private RuntimeCacheSyncService runtimeCacheSyncService;

    @InjectMocks
    private ClaimValidationDecisionProcessor claimValidationDecisionProcessor;

    @BeforeEach
    void setUp() {
        // Ensure clean mock state before each test execution
    }

    @Test
    void runtime_cache_sync_failure() {
        // given
        String claimId = "claim-std-001";
        Map<String, Object> payload = Map.of("claimId", claimId, "status", "PENDING");
        RuntimeException cacheSyncFailure = new RuntimeException("Runtime cache sync failed: connection timeout");

        when(runtimeCacheSyncService.syncRuntimeCache(claimId, payload))
                .thenThrow(cacheSyncFailure);

        // when & then
        assertThrows(RuntimeException.class, () -> {
            claimValidationDecisionProcessor.processValidationDecision(claimId, payload);
        }).hasMessageContaining("Runtime cache sync failed");

        verify(runtimeCacheSyncService, times(1)).syncRuntimeCache(claimId, payload);
    }
}
