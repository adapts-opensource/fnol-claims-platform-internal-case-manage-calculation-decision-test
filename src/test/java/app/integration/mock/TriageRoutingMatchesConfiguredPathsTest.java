package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TriageRoutingMatchesConfiguredPathsTest {

    @Mock
    private RulesEngineServiceDynamodb rulesEngineService;

    @Mock
    private PolicyValidationServiceDynamodb policyValidationService;

    @InjectMocks
    private ClaimDataStandardizationDecisionEngine decisionEngine;

    private static final String TEST_ENTITY_ID = "claim-data-std-001";
    private static final String EXPECTED_CONFIGURED_PATH = "/triage/standard/auto-medical";

    @BeforeEach
    void setUp() {
        // Mocks initialized automatically by MockitoExtension
    }

    @Test
    void triage_routing_matches_configured_paths() {
        // Arrange
        Map<String, Object> payload = Map.of(
                "claimType", "AUTO",
                "severity", "MEDIUM",
                "jurisdiction", "US-CA",
                "injuryType", "WHIPLASH"
        );

        Map<String, Object> routingRules = Map.of(
                "path", EXPECTED_CONFIGURED_PATH,
                "priority", 1,
                "requiresManualReview", false
        );
        when(rulesEngineService.fetchRules(anyString())).thenReturn(routingRules);
        when(policyValidationService.validatePayload(anyMap())).thenReturn(true);

        var claimData = new ClaimDataStandardizationTransformationValida(TEST_ENTITY_ID, payload);
        var decision = decisionEngine.evaluateTriage(claimData);

        // Assert
        assertNotNull(decision, "Triage decision should not be null");
        assertEquals(EXPECTED_CONFIGURED_PATH, decision.getRoutingPath(),
                "Triage routing should match the configured path from rules engine");
        assertTrue(decision.isValidationPassed(), "Payload validation should pass");

        // Verify infra interactions
        verify(rulesEngineService, times(1)).fetchRules(TEST_ENTITY_ID);
        verify(policyValidationService, times(1)).validatePayload(payload);
    }

    // Minimal SUT demonstrating integration logic
    static class ClaimDataStandardizationDecisionEngine {
        private final RulesEngineServiceDynamodb rulesEngineService;
        private final PolicyValidationServiceDynamodb policyValidationService;

        ClaimDataStandardizationDecisionEngine(RulesEngineServiceDynamodb rulesEngineService, PolicyValidationServiceDynamodb policyValidationService) {
            this.rulesEngineService = rulesEngineService;
            this.policyValidationService = policyValidationService;
        }

        public TriageDecision evaluateTriage(ClaimDataStandardizationTransformationValida claimData) {
            Map<String, Object> rules = rulesEngineService.fetchRules(claimData.getId());
            boolean isValid = policyValidationService.validatePayload(claimData.getPayload());

            String path = (String) rules.getOrDefault("path", "/triage/default");
            return new TriageDecision(path, isValid);
        }
    }

    static class ClaimDataStandardizationTransformationValida {
        private final String id;
        private final Map<String, Object> payload;

        ClaimDataStandardizationTransformationValida(String id, Map<String, Object> payload) {
            this.id = id;
            this.payload = payload;
        }

        String getId() { return id; }
        Map<String, Object> getPayload() { return payload; }
    }

    static class TriageDecision {
        private final String routingPath;
        private final boolean validationPassed;

        TriageDecision(String routingPath, boolean validationPassed) {
            this.routingPath = routingPath;
            this.validationPassed = validationPassed;
        }

        String getRoutingPath() { return routingPath; }
        boolean isValidationPassed() { return validationPassed; }
    }

    interface RulesEngineServiceDynamodb {
        Map<String, Object> fetchRules(String entityKey);
    }

    interface PolicyValidationServiceDynamodb {
        boolean validatePayload(Map<String, Object> payload);
    }
}
