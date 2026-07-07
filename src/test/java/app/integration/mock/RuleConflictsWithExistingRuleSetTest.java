package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleConflictsWithExistingRuleSetTest {

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @InjectMocks
    private ClaimDecisionEnrichmentService claimDecisionEnrichmentService;

    @Test
    void rule_conflicts_with_existing_rule_set() {
        // Given
        String claimId = "CLM-2024-STD-001";
        Map<String, Object> enrichmentPayload = Map.of(
                "ruleId", "RULE-ENRICH-01",
                "action", "UPDATE_COVERAGE",
                "source", "EXTERNAL_PRICING"
        );

        when(rulesEngineService.checkForConflicts(eq(claimId), anyMap()))
                .thenThrow(new RuleConflictException("Conflict detected: RULE-ENRICH-01 overlaps with existing active rule set for claim " + claimId));

        // When & Then
        RuleConflictException thrown = assertThrows(
                RuleConflictException.class,
                () -> claimDecisionEnrichmentService.enrichClaimData(claimId, enrichmentPayload)
        );
        assertNotNull(thrown);
        assertTrue(thrown.getMessage().contains("overlaps with existing active rule set"));

        // Verify compliance/observability NFRs: Audit log recorded before failure propagation
        verify(auditDiaryStore).writeAuditEntry(
                eq("CLAIM_ENRICHMENT_CONFLICT"),
                eq(claimId),
                anyMap()
        );
        // Verify no DynamoDB mutation occurs on conflict
        verify(rulesEngineService, never()).persistDecision(anyString(), anyMap());
    }

    static class RuleConflictException extends RuntimeException {
        RuleConflictException(String message) { super(message); }
    }
}
