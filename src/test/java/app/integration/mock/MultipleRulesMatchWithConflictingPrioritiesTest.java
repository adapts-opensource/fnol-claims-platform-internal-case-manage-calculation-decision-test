package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Mock tests for Claim Initiation & Routing:decision:calculation.
 * Validates decision logic with mocked rule engines and infrastructure I/O.
 */
public class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private RuleEngine ruleEngine;

    @Mock
    private ReferenceDataCache cache;

    @InjectMocks
    private ClaimDecisionCalculator calculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("multiple_rules_match_with_conflicting_priorities")
    void multipleRulesMatchWithConflictingPriorities() {
        // Arrange: Payload triggering multiple rules with different priorities
        Map<String, Object> payload = Map.of(
            "claimType", "AUTO",
            "severity", "HIGH",
            "damageAmount", 5000,
            "policyHolderId", "PH-12345"
        );

        // Simulate rules matching with conflicting priorities
        // Priority 20 should win over 15 and 10
        List<RuleMatch> matchingRules = List.of(
            new RuleMatch("RULE_AUTO_STANDARD", "Standard_Auto_Routing", 10),
            new RuleMatch("RULE_HIGH_SEVERITY", "High_Severity_Routing", 20),
            new RuleMatch("RULE_LARGE_CLAIM", "Large_Claim_Routing", 15)
        );

        // Mock Rule Engine to return matches
        when(ruleEngine.evaluate(anyMap())).thenReturn(matchingRules);
        
        // Mock Cache to simulate miss (rules fetched from source)
        when(cache.get(anyString())).thenReturn(null);

        // Act: Calculate decision
        DecisionResult result = calculator.calculate(payload);

        // Assert: Verify highest priority rule is selected
        assertNotNull(result, "Decision result should not be null");
        assertEquals("High_Severity_Routing", result.getRoutingDestination(),
            "Routing destination should match the rule with highest priority");
        assertEquals(20, result.getAppliedRulePriority(),
            "Applied priority should be the maximum priority among matches");
        assertEquals("RULE_HIGH_SEVERITY", result.getAppliedRuleId());

        // Verify interactions
        verify(ruleEngine).evaluate(payload);
        verify(cache).get(eq("rules:auto"));
        verifyNoMoreInteractions(ruleEngine, cache);
    }

    /**
     * Stubbed classes and interfaces to support test compilation and structure.
     * In a real project, these would be defined in src/main/java.
     */
    
    public interface RuleEngine {
        List<RuleMatch> evaluate(Map<String, Object> payload);
    }

    public interface ReferenceDataCache {
        String get(String key);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RuleMatch {
        private String ruleId;
        private String routingDestination;
        private int priority;
    }

    @lombok.Data
    @lombok.RequiredArgsConstructor
    public static class DecisionResult {
        private String routingDestination;
        private int appliedRulePriority;
        private String appliedRuleId;
    }

    public class ClaimDecisionCalculator {
        private RuleEngine ruleEngine;
        private ReferenceDataCache cache;

        public ClaimDecisionCalculator(RuleEngine ruleEngine, ReferenceDataCache cache) {
            this.ruleEngine = ruleEngine;
            this.cache = cache;
        }

        public DecisionResult calculate(Map<String, Object> payload) {
            String cacheKey = "rules:" + payload.getOrDefault("claimType", "UNKNOWN").toString().toLowerCase();
            String cachedRules = cache.get(cacheKey);
            
            // In mock scenario, cache is null, so we call engine
            List<RuleMatch> matches = ruleEngine.evaluate(payload);
            
            if (matches.isEmpty()) {
                throw new IllegalStateException("No rules matched");
            }

            // Conflict Resolution: Select rule with highest priority
            RuleMatch winner = matches.stream()
                .max(Comparator.comparingInt(RuleMatch::getPriority))
                .orElseThrow(() -> new IllegalStateException("No winner found"));

            return new DecisionResult(
                winner.getRoutingDestination(),
                winner.getPriority(),
                winner.getRuleId()
            );
        }
    }
}
