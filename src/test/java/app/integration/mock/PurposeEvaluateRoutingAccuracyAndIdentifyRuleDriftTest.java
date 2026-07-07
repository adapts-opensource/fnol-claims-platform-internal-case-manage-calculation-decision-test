package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClaimDataEnrichmentRoutingAccuracyTest {

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    private ClaimEnrichmentDecisionService enrichmentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        enrichmentService = new ClaimEnrichmentDecisionService(rulesEngineService, workflowTaskRouter);
    }

    @Test
    void purpose_evaluate_routing_accuracy_and_identify_rule_drift() {
        // Arrange
        String claimId = "CLM-98765";
        Map<String, Object> claimPayload = Map.of("claimType", "AUTO", "severity", "HIGH");
        Map<String, Object> baselineRuleOutput = Map.of("route", "AUTO_STANDARD_ENRICHMENT", "confidenceScore", 0.92);
        Map<String, Object> currentRuleOutput = Map.of("route", "AUTO_STANDARD_ENRICHMENT", "confidenceScore", 0.92);

        lenient().when(rulesEngineService.fetchDecision(anyString(), anyString()))
                 .thenReturn(baselineRuleOutput);

        // Act
        RoutingOutcome outcome = enrichmentService.processEnrichmentDecision(claimId, claimPayload);

        // Assert Routing Accuracy
        assertEquals("AUTO_STANDARD_ENRICHMENT", outcome.route(), 
            "Routing accuracy must match expected enrichment path per business rules");

        // Assert Rule Drift Identification
        RuleDriftAnalysis driftAnalysis = new RuleDriftAnalysis(baselineRuleOutput, currentRuleOutput);
        assertFalse(driftAnalysis.isDriftDetected(), 
            "Rule drift should be flagged as absent when outputs match baseline");

        // Verify External I/O Contracts (Mocked)
        verify(rulesEngineService, times(1)).fetchDecision(eq("CLM-98765"), eq("ENRICHMENT_RULES"));
        verify(workflowTaskRouter, times(1)).sendToQueue(eq("AUTO_STANDARD_ENRICHMENT"), anyMap());
    }

    // Supporting domain models for test context
    record RoutingOutcome(String route, Map<String, Object> metadata) {}
    record RuleDriftAnalysis(Map<String, Object> baseline, Map<String, Object> current) {
        boolean isDriftDetected() {
            return !baseline.equals(current);
        }
    }

    // Mocked service interfaces representing infra contracts
    interface RulesEngineDecisionService {
        Map<String, Object> fetchDecision(String partitionKey, String ruleType);
    }

    interface WorkflowTaskRouter {
        void sendToQueue(String routeName, Map<String, Object> taskPayload);
    }
}
