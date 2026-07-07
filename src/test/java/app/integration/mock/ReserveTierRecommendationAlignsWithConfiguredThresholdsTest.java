package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionEnrichmentMockTest {

    @Mock
    private TierConfigService tierConfigService;

    @Mock
    private Logger structuredLogger;

    private ClaimDecisionEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimDecisionEnrichmentService(tierConfigService, structuredLogger);
    }

    @Test
    void reserve_tier_recommendation_aligns_with_configured_thresholds() {
        // Given
        String claimId = "claim-123";
        Map<String, Object> payload = new HashMap<>();
        payload.put("reserveAmount", 2500.0);
        payload.put("claimType", "AUTO");

        Map<String, Double> thresholds = new HashMap<>();
        thresholds.put("low", 1000.0);
        thresholds.put("medium", 5000.0);
        thresholds.put("high", 10000.0);
        when(tierConfigService.getThresholds()).thenReturn(thresholds);

        // When
        Map<String, Object> enrichedPayload = enrichmentService.enrich(claimId, payload);

        // Then
        assertNotNull(enrichedPayload);
        assertEquals("MEDIUM", enrichedPayload.get("reserveTier"));
        assertEquals("AUTO", enrichedPayload.get("claimType"));
        assertEquals(2500.0, enrichedPayload.get("reserveAmount"));
        verify(structuredLogger, times(1)).info("Enrichment completed for claim: " + claimId);
        verify(tierConfigService, times(1)).getThresholds();
    }

    interface TierConfigService {
        Map<String, Double> getThresholds();
    }

    static class ClaimDecisionEnrichmentService {
        private final TierConfigService tierConfigService;
        private final Logger logger;

        ClaimDecisionEnrichmentService(TierConfigService tierConfigService, Logger logger) {
            this.tierConfigService = tierConfigService;
            this.logger = logger;
        }

        Map<String, Object> enrich(String id, Map<String, Object> payload) {
            Map<String, Double> thresholds = tierConfigService.getThresholds();
            double amount = ((Number) payload.getOrDefault("reserveAmount", 0.0)).doubleValue();
            String tier;
            if (amount < thresholds.get("low")) {
                tier = "LOW";
            } else if (amount < thresholds.get("medium")) {
                tier = "MEDIUM";
            } else {
                tier = "HIGH";
            }
            Map<String, Object> enriched = new HashMap<>(payload);
            enriched.put("reserveTier", tier);
            enriched.put("validatedAt", System.currentTimeMillis());
            logger.info("Enrichment completed for claim: " + id);
            return enriched;
        }
    }
}
