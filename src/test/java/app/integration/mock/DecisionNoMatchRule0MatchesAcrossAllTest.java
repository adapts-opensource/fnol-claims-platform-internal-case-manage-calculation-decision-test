package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionEnrichmentMockTest {

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private ClaimEnrichmentService claimEnrichmentService;

    private ClaimDataStandardizationEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimDataStandardizationEnrichmentService(
                rulesEngineDecisionService,
                workflowTaskRouter,
                auditDiaryStore,
                claimEnrichmentService
        );
    }

    @Test
    void decision_no_match_rule_0_matches_across_all_keys_expected_outcome_create_unmatched_fnol_shell() {
        // Arrange
        String claimId = "CLM-STD-TEST-001";
        Map<String, Object> inputPayload = Map.of("claimId", claimId, "policyNumber", "POL-987", "type", "FNOL");

        // Mock rules engine to return 0 matches across all keys
        when(rulesEngineDecisionService.evaluateRules(claimId, inputPayload))
                .thenReturn(Collections.emptyList());

        // Mock audit store for structured logging/compliance
        when(auditDiaryStore.writeAudit(anyString(), anyString(), any(Map.class)))
                .thenReturn("s3://AuditDiaryStore-bucket/AuditDiaryStore/" + claimId + ".json");

        // Mock FNOL shell creation for unmatched outcome
        String expectedShellId = "SHELL-UNMATCHED-001";
        when(claimEnrichmentService.createUnmatchedShell(claimId, inputPayload))
                .thenReturn(expectedShellId);

        // Act
        String actualShellId = enrichmentService.processEnrichmentDecision(claimId, inputPayload);

        // Assert
        assertEquals(expectedShellId, actualShellId, "Expected unmatched FNOL shell to be created when 0 matches occur");
        verify(rulesEngineDecisionService).evaluateRules(claimId, inputPayload);
        verify(claimEnrichmentService).createUnmatchedShell(claimId, inputPayload);
        verify(auditDiaryStore).writeAudit(eq(claimId), eq("ENRICHMENT_DECISION"), any(Map.class));
        verify(workflowTaskRouter, never()).routeToSpecialHandling(anyString(), any());
    }
}
