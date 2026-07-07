package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionEnrichmentTest {

    @Mock
    private RuleChangeSubmissionClient ruleChangeClient;

    @Mock
    private DecisionEnrichmentEngine enrichmentEngine;

    private ClaimStandardizationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ClaimStandardizationProcessor(ruleChangeClient, enrichmentEngine);
    }

    @Test
    void applies_when_config_admin_submits_rule_change() {
        // Given: Config admin submits a rule change payload
        String adminId = "config-admin-01";
        Map<String, Object> ruleChangePayload = Map.of(
                "ruleId", "RL-2024-001",
                "enrichmentType", "DECISION_STANDARDIZATION",
                "parameters", Map.of("threshold", 90, "autoApprove", true)
        );

        when(ruleChangeClient.submitRuleChange(adminId, ruleChangePayload))
                .thenReturn(Map.of("status", "APPLIED", "ruleId", "RL-2024-001"));

        String claimId = "CLM-98765";
        Map<String, Object> claimPayload = Map.of(
                "id", claimId,
                "claimAmount", 12000,
                "status", "PENDING_REVIEW"
        );

        // When: Processor triggers decision enrichment based on the new rule
        Map<String, Object> result = processor.applyEnrichmentDecision(claimId, claimPayload, ruleChangePayload);

        // Then: System applies the rule change to the decision enrichment process
        assertNotNull(result);
        assertEquals("APPLIED", result.get("status"));
        assertEquals("RL-2024-001", result.get("activeRuleId"));
        assertTrue((Boolean) result.get("autoApprove"));

        verify(ruleChangeClient, times(1)).submitRuleChange(eq(adminId), anyMap());
        verify(enrichmentEngine, times(1)).processClaimData(eq(claimPayload), anyMap());
    }
}
