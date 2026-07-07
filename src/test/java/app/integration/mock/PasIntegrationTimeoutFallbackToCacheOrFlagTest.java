package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionTransformationTest {

    @Mock
    private PasIntegrationClient pasIntegrationClient;
    @Mock
    private CacheService cacheService;
    @Mock
    private ManualReviewFlagService manualReviewFlagService;

    private ClaimDataTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = new ClaimDataTransformationService(
            pasIntegrationClient, cacheService, manualReviewFlagService
        );
    }

    @Test
    void pasIntegrationTimeoutFallbackToCacheOrFlagForManualReview() {
        // Arrange
        String claimId = "claim-123";
        Map<String, Object> originalPayload = Map.of("claimId", claimId, "type", "AUTO");
        ClaimDataStandardizationCalculationTransform input = new ClaimDataStandardizationCalculationTransform(claimId, originalPayload);

        // Simulate PAS integration timeout
        when(pasIntegrationClient.fetchStandardizedData(claimId))
            .thenThrow(new TimeoutException("PAS integration timeout after 5000ms"));

        // Mock cache fallback returning standardized data
        Map<String, Object> cachedPayload = Map.of("claimId", claimId, "type", "AUTO", "status", "STANDARDIZED");
        when(cacheService.getStandardizedPayload(claimId)).thenReturn(Optional.of(cachedPayload));

        // Act
        ClaimDataStandardizationCalculationTransform result = transformationService.transform(input);

        // Assert
        assertNotNull(result);
        assertEquals(claimId, result.getId());
        assertEquals("STANDARDIZED", result.getPayload().get("status"));
        verify(pasIntegrationClient).fetchStandardizedData(claimId);
        verify(cacheService).getStandardizedPayload(claimId);
        verifyNoInteractions(manualReviewFlagService);
    }
}

// Supporting interfaces and models for test isolation
interface PasIntegrationClient {
    Map<String, Object> fetchStandardizedData(String claimId) throws TimeoutException;
}

interface CacheService {
    Optional<Map<String, Object>> getStandardizedPayload(String claimId);
}

interface ManualReviewFlagService {
    void flagForReview(String claimId, String reason);
}

class ClaimDataStandardizationCalculationTransform {
    private final String id;
    private final Map<String, Object> payload;

    public ClaimDataStandardizationCalculationTransform(String id, Map<String, Object> payload) {
        this.id = id;
        this.payload = payload;
    }

    public String getId() { return id; }
    public Map<String, Object> getPayload() { return payload; }
}

class ClaimDataTransformationService {
    private final PasIntegrationClient pasIntegrationClient;
    private final CacheService cacheService;
    private final ManualReviewFlagService manualReviewFlagService;

    public ClaimDataTransformationService(PasIntegrationClient pasIntegrationClient,
                                          CacheService cacheService,
                                          ManualReviewFlagService manualReviewFlagService) {
        this.pasIntegrationClient = pasIntegrationClient;
        this.cacheService = cacheService;
        this.manualReviewFlagService = manualReviewFlagService;
    }

    public ClaimDataStandardizationCalculationTransform transform(ClaimDataStandardizationCalculationTransform input) {
        String claimId = input.getId();
        try {
            // Simulate TLS-secured, least-privileged HTTP call to external PAS
            Map<String, Object> standardizedData = pasIntegrationClient.fetchStandardizedData(claimId);
            return new ClaimDataStandardizationCalculationTransform(claimId, standardizedData);
        } catch (TimeoutException e) {
            // Fallback to cache as per NFR availability & operability
            Optional<Map<String, Object>> cachedData = cacheService.getStandardizedPayload(claimId);
            if (cachedData.isPresent()) {
                return new ClaimDataStandardizationCalculationTransform(claimId, cachedData.get());
            }
            // Fallback to manual review flag when cache miss occurs
            manualReviewFlagService.flagForReview(claimId, "PAS_TIMEOUT_CACHE_EMPTY");
            return new ClaimDataStandardizationCalculationTransform(claimId, input.getPayload());
        }
    }
}
