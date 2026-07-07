package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionEnrichmentMockTest {

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @InjectMocks
    private ClaimDataStandardizationDecisionEnrichmentService enrichmentService;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimPayload = new HashMap<>();
        claimPayload.put("claimId", "CLM-102938");
        claimPayload.put("decision", "Approved");
        claimPayload.put("approverSignedOff", true);
        claimPayload.put("standardizedData", Map.of("type", "FNOL", "version", "1.0"));
    }

    @Test
    void decision_approved_rule_approver_signs_off_expected_outcome_activate_rule() {
        // Arrange: Mock decision service to simulate approved decision and rule activation
        when(rulesEngineDecisionService.evaluateDecision(anyString(), any(Map.class)))
                .thenReturn(Map.of("ruleStatus", "ACTIVE", "ruleActivated", true, "enrichmentTimestamp", "2024-01-15T10:30:00Z"));

        // Act: Process claim data enrichment and rule evaluation
        Map<String, Object> enrichedResult = enrichmentService.processEnrichment("CLM-102938", claimPayload);

        // Assert: Verify rule activation outcome and mocked external I/O interactions
        assertNotNull(enrichedResult, "Enriched result should not be null");
        assertEquals("ACTIVE", enrichedResult.get("ruleStatus"), "Rule status should be ACTIVE");
        assertTrue((Boolean) enrichedResult.get("ruleActivated"), "Rule should be activated");
        
        // Verify decision service was called with correct claim ID and payload
        verify(rulesEngineDecisionService, times(1))
                .evaluateDecision(eq("CLM-102938"), any(Map.class));
        
        // Verify audit diary store was called for compliance/observability NFRs
        verify(auditDiaryStore, times(1))
                .writeAuditEvent(anyString(), any(Map.class));
    }
}
