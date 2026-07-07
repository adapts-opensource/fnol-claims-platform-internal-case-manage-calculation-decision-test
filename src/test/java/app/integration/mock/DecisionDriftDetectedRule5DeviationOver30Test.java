package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization:decision:enrichment.
 * Verifies DecisionDriftDetectedRule5DeviationOver30 scenario.
 */
@ExtendWith(MockitoExtension.class)
public class DecisionDriftDetectedRule5DeviationOver30Test {

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    @Mock
    private WorkflowTaskRouter workflowRouter;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @InjectMocks
    private ClaimDataStandardizationEnrichmentService enrichmentService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    @Captor
    private ArgumentCaptor<String> alertRecipientCaptor;

    @BeforeEach
    void setUp() {
        // Initialize mocks if required by specific service implementations
    }

    /**
     * Test Case: DecisionDriftDetectedRule5DeviationOver30
     * Description: Verify that when deviation > 5% over 30 days is detected,
     * the enrichment process sets decision to "Drift Detected?" and routes an alert to the configuration team.
     */
    @Test
    void decision_drift_detected_rule_5_deviation_over_30_days_expected_outcome_alert_configuration_team() {
        // Given: Claim payload with metrics indicating >5% deviation over 30 days
        Map<String, Object> claimPayload = new HashMap<>();
        claimPayload.put("claimId", "CLM-DRIFT-TEST-001");
        claimPayload.put("standardizationData", Map.of(
                "historicalMetrics", Map.of("deviation30Days", 6.8), // 6.8% > 5% threshold
                "period", "30_DAYS"
        ));

        // Mock RulesEngineDecisionService to return Drift Detected decision
        when(rulesEngineService.evaluate(any(Map.class))).thenReturn(Map.of(
                "decision", "Drift Detected?",
                "ruleId", "RULE_5_DEV_30",
                "threshold", "5.0",
                "actual", "6.8",
                "outcome", "Alert configuration team"
        ));

        // When: Enrichment service processes the claim
        Map<String, Object> enrichedPayload = enrichmentService.enrich(claimPayload);

        // Then: Verify enrichment fields are updated
        assertNotNull(enrichedPayload, "Enriched payload should not be null");
        assertEquals("Drift Detected?", enrichedPayload.get("decision"), "Decision should indicate drift");
        assertEquals("Alert configuration team", enrichedPayload.get("outcome"), "Outcome should match alert instruction");
        assertEquals("CLM-DRIFT-TEST-001", enrichedPayload.get("claimId"), "Claim ID should be preserved");

        // Verify Alert Routing to Configuration Team
        verify(workflowRouter).createTask(
                alertRecipientCaptor.capture(),
                eq("Drift_Alert_Task"),
                payloadCaptor.capture()
        );

        assertEquals("configuration_team", alertRecipientCaptor.getValue(),
                "Alert should be routed to configuration team");

        Map<String, Object> capturedTaskPayload = payloadCaptor.getValue();
        assertEquals("CLM-DRIFT-TEST-001", capturedTaskPayload.get("claimId"),
                "Task payload should contain claim context");
        assertEquals("Drift Detected?", capturedTaskPayload.get("decision"),
                "Task payload should reflect drift decision");

        // Verify Audit Logging to S3
        verify(auditDiaryStore).writeAuditLog(
                eq("AuditDiaryStore-bucket"),
                eq("AuditDiaryStore/CLM-DRIFT-TEST-001.json"),
                anyString()
        );
    }
}
