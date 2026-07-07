package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Mock interfaces representing infrastructure and domain services
interface RuleEngineService {
    boolean evaluateRules(Map payload);
}

interface FallbackRoutingService {
    String determineFallbackRoute(Map payload);
}

interface DecisionValidationService {
    void validateDecision(String id, Map result);
}

interface CacheService {
    void cacheDecision(String key, Map result);
}

// Service under test
class ClaimRoutingDecisionCalculator {
    private final RuleEngineService ruleEngine;
    private final FallbackRoutingService fallbackRouter;
    private final DecisionValidationService validator;
    private final CacheService cache;

    ClaimRoutingDecisionCalculator(RuleEngineService ruleEngine,
                                   FallbackRoutingService fallbackRouter,
                                   DecisionValidationService validator,
                                   CacheService cache) {
        this.ruleEngine = ruleEngine;
        this.fallbackRouter = fallbackRouter;
        this.validator = validator;
        this.cache = cache;
    }

    Map calculateDecision(String id, Map payload) {
        Map result = new HashMap<>();
        result.put("id", id);
        
        if (!ruleEngine.evaluateRules(payload)) {
            String fallbackRoute = fallbackRouter.determineFallbackRoute(payload);
            result.put("routingStrategy", fallbackRoute);
            result.put("source", "FALLBACK");
        } else {
            result.put("routingStrategy", "RULE_BASED");
            result.put("source", "RULES");
        }
        
        validator.validateDecision(id, result);
        cache.cacheDecision("Cache & Reference Data:cache:" + id, result);
        return result;
    }
}

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionCalculationMockTest {
    @Mock
    private RuleEngineService ruleEngine;

    @Mock
    private FallbackRoutingService fallbackRouter;

    @Mock
    private DecisionValidationService validator;

    @Mock
    private CacheService cache;

    @InjectMocks
    private ClaimRoutingDecisionCalculator calculator;

    @BeforeEach
    void setUp() {
        // Mocks are reset automatically by MockitoExtension
    }

    @Test
    void fallbackRoutingWorksWhenRulesFail() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        Map payload = new HashMap<>();
        payload.put("claimType", "AUTO");
        payload.put("damageAmount", 2500.00);
        payload.put("jurisdiction", "US-CA");

        // Simulate rule engine failure
        when(ruleEngine.evaluateRules(payload)).thenReturn(false);
        
        // Simulate fallback routing success
        String expectedFallbackRoute = "REGIONAL_HANDLER_US_WEST";
        when(fallbackRouter.determineFallbackRoute(payload)).thenReturn(expectedFallbackRoute);

        // Act
        Map result = calculator.calculateDecision(claimId, payload);

        // Assert
        assertNotNull(result);
        assertEquals(claimId, result.get("id"));
        assertEquals(expectedFallbackRoute, result.get("routingStrategy"));
        assertEquals("FALLBACK", result.get("source"));

        // Verify interactions
        verify(ruleEngine).evaluateRules(payload);
        verify(fallbackRouter).determineFallbackRoute(payload);
        verify(validator).validateDecision(eq(claimId), any(Map.class));
        verify(cache).cacheDecision(eq("Cache & Reference Data:cache:" + claimId), any(Map.class));
    }
}
