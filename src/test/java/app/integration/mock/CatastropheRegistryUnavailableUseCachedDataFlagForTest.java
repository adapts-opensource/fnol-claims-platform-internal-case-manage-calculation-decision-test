package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatastropheRegistryUnavailableUseCachedDataFlagForReviewTest {

    @Mock
    private CatastropheRegistryClient registryClient;

    @Mock
    private CacheService cacheService;

    @Mock
    private DataStoreRepository dataStoreRepository;

    @Mock
    private StructuredLogger logger;

    private StateTransitionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new StateTransitionCalculator(registryClient, cacheService, dataStoreRepository, logger);
    }

    @Test
    void catastrophe_registry_unavailable_use_cached_data_flag_for_review() {
        // Arrange
        String submissionId = "fnol-8f3a2c1b-9d4e";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", submissionId);
        payload.put("channel", "WEB");
        payload.put("region", "US-EAST");

        Map<String, Object> cachedCatastropheData = Map.of(
            "catastrophe_zone", "ZONE-A",
            "risk_score", 85,
            "source", "CACHE"
        );

        // Simulate registry unavailability to trigger fallback
        when(registryClient.fetchData(anyString())).thenThrow(new RuntimeException("Registry unavailable"));
        when(cacheService.retrieve(anyString())).thenReturn(cachedCatastropheData);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        // Act
        Map<String, Object> resultPayload = calculator.calculate(submissionId, payload);

        // Assert
        assertNotNull(resultPayload, "Result payload must not be null");
        assertEquals(submissionId, resultPayload.get("id"));
        assertTrue((Boolean) resultPayload.get("flag_for_review"), "Submission must be flagged for review");
        assertSame(cachedCatastropheData, resultPayload.get("catastrophe_data"), "Must use cached catastrophe data");

        verify(dataStoreRepository).saveOrUpdate(eq(submissionId), payloadCaptor.capture());
        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertTrue((Boolean) capturedPayload.get("flag_for_review"));
        assertSame(cachedCatastropheData, capturedPayload.get("catastrophe_data"));

        verify(logger).warn("Catastrophe registry unavailable. Falling back to cached data and flagging for review.",
            Map.of("submission_id", submissionId));
        verifyNoMoreInteractions(registryClient);
    }
}
