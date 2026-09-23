package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeduplicationCacheUnavailableTest {

    @Mock
    private DeduplicationCache deduplicationCache;

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private Logger logger;

    @InjectMocks
    private FnolValidationService fnolValidationService;

    @Test
    void deduplication_cache_unavailable() {
        // Given: Deduplication cache is unavailable and throws an exception
        when(deduplicationCache.isDuplicate(anyString(), anyString()))
                .thenThrow(new RuntimeException("Deduplication cache unavailable"));

        String policyId = "POL-12345";
        String claimId = "CLM-67890";
        String tenantId = "TENANT-001";

        // When: FNOL submission validation triggers decision logic
        var decision = fnolValidationService.validateClaimSubmission(policyId, claimId, tenantId);

        // Then: System handles cache failure gracefully via fallback to persistence
        assertNotNull(decision);
        assertEquals(ValidationDecision.PROCEED_WITH_DB_CHECK, decision);
        verify(deduplicationCache, times(1)).isDuplicate(policyId, claimId);
        verify(claimRepository, times(1)).findByPolicyIdAndClaimId(policyId, claimId);
        verify(logger).warn("Deduplication cache unavailable for tenant {}, falling back to persistence layer", tenantId);
    }
}
