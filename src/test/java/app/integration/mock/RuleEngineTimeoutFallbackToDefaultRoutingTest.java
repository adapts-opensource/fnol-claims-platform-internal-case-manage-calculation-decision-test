package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleEngineTimeoutFallbackToDefaultRoutingTest {

    @Mock
    private RuleEngineClient ruleEngineClient;

    @Mock
    private CacheService cacheService;

    @Mock
    private Logger logger;

    private ClaimRoutingService claimRoutingService;

    @BeforeEach
    void setUp() {
        claimRoutingService = new ClaimRoutingService(ruleEngineClient, cacheService, logger);
    }

    @Test
    void rule_engine_timeout_fallback_to_default_routing() {
        // Given
        String claimId = "CLM-12345";
        Map<String, Object> payload = Map.of("claimType", "AUTO", "severity", "HIGH");
        Map<String, Object> defaultRouting = Map.of("routeTo", "ADJUSTER_POOL_A", "priority", "HIGH");

        when(ruleEngineClient.calculateRouting(payload)).thenThrow(new TimeoutException("Rule engine timeout"));
        when(cacheService.getOrDefault(eq("routing:default"), any())).thenReturn(defaultRouting);

        // When
        Map<String, Object> result = claimRoutingService.calculateRoutingDecision(claimId, payload);

        // Then
        assertNotNull(result, "Routing decision should not be null after fallback");
        assertEquals("ADJUSTER_POOL_A", result.get("routeTo"), "Should fallback to default routeTo");
        assertEquals("HIGH", result.get("priority"), "Should fallback to default priority");
        
        verify(ruleEngineClient, times(1)).calculateRouting(payload);
        verify(cacheService, times(1)).getOrDefault(eq("routing:default"), any());
        verify(logger, times(1)).warn(eq("Rule engine timeout, falling back to default routing"), any(TimeoutException.class));
    }

    // Minimal stub classes to ensure standalone compilation for mock testing
    static class ClaimRoutingService {
        private final RuleEngineClient ruleEngineClient;
        private final CacheService cacheService;
        private final Logger logger;

        ClaimRoutingService(RuleEngineClient ruleEngineClient, CacheService cacheService, Logger logger) {
            this.ruleEngineClient = ruleEngineClient;
            this.cacheService = cacheService;
            this.logger = logger;
        }

        Map<String, Object> calculateRoutingDecision(String claimId, Map<String, Object> payload) {
            try {
                return ruleEngineClient.calculateRouting(payload);
            } catch (TimeoutException e) {
                logger.warn("Rule engine timeout, falling back to default routing", e);
                return cacheService.getOrDefault("routing:default", Map.of("routeTo", "DEFAULT_POOL", "priority", "NORMAL"));
            } catch (Exception e) {
                logger.error("Unexpected error during routing calculation", e);
                throw new RuntimeException("Routing calculation failed", e);
            }
        }
    }

    interface RuleEngineClient {
        Map<String, Object> calculateRouting(Map<String, Object> payload);
    }

    interface CacheService {
        Map<String, Object> getOrDefault(String key, Map<String, Object> defaultValue);
    }
}
