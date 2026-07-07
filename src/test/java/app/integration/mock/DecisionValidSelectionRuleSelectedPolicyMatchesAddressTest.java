package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionEnrichmentTest {

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    private ClaimEnrichmentService claimEnrichmentService;

    @BeforeEach
    void setUp() {
        claimEnrichmentService = new ClaimEnrichmentService(rulesEngineService, auditDiaryStore);
    }

    @Test
    void decision_valid_selection_rule_selected_policy_matches_address_insured_date_and_is_active_expected_outcome_update_shell_proceed_to_triage() {
        // Arrange
        String claimId = "claim-std-001";
        Map<String, Object> claimPayload = Map.of(
                "policyId", "POL-2024-001",
                "insuredName", "Jane Smith",
                "policyStartDate", "2024-01-01",
                "policyEndDate", "2025-12-31",
                "addressLine1", "100 Insurance Way",
                "city", "Springfield",
                "state", "IL",
                "zipCode", "62701"
        );

        Map<String, Object> expectedRuleOutcome = Map.of(
                "decision", "Valid Selection?",
                "rule", "Selected policy matches address/insured/date and is active",
                "outcome", "Update shell, proceed to triage",
                "isPolicyActive", true,
                "addressMatch", true,
                "insuredMatch", true,
                "dateValid", true
        );

        when(rulesEngineService.fetchDecisionRuleResult(anyString(), anyMap())).thenReturn(expectedRuleOutcome);

        // Act
        Map<String, Object> enrichedPayload = claimEnrichmentService.processEnrichment(claimId, claimPayload);

        // Assert
        assertNotNull(enrichedPayload, "Enriched payload should not be null");
        assertEquals("Update shell, proceed to triage", enrichedPayload.get("outcome"), "Outcome should match expected triage instruction");
        assertEquals("Valid Selection?", enrichedPayload.get("decision"), "Decision field should be populated");
        verify(rulesEngineService, times(1)).fetchDecisionRuleResult(anyString(), anyMap());
        verify(auditDiaryStore, times(1)).writeAuditLog(anyString(), anyMap());
    }
}
