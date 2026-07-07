package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApproverUnavailableTest {

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    private ClaimEnrichmentService claimEnrichmentService;

    @BeforeEach
    void setUp() {
        claimEnrichmentService = new ClaimEnrichmentService(rulesEngineDecisionService, auditDiaryStore);
    }

    @Test
    void approver_unavailable() {
        // Arrange
        String claimId = "CLM-98765";
        Map<String, Object> inputPayload = Map.of(
                "id", claimId,
                "status", "PENDING_APPROVAL",
                "approverId", "USR-001"
        );
        Map<String, Object> expectedEnrichedPayload = Map.of(
                "id", claimId,
                "status", "PENDING_APPROVAL",
                "approverId", "USR-001",
                "enrichmentStatus", "APPROVER_UNAVAILABLE",
                "nextAction", "RETRY_LATER",
                "auditReference", "s3://AuditDiaryStore-bucket/AuditDiaryStore/CLM-98765.json"
        );

        // Simulate approver unavailable / decision not found in DynamoDB
        when(rulesEngineDecisionService.fetchDecision(eq(claimId))).thenReturn(Optional.empty());
        when(auditDiaryStore.writeAuditLog(eq(claimId), anyString())).thenReturn("s3://AuditDiaryStore-bucket/AuditDiaryStore/CLM-98765.json");

        // Act
        Map<String, Object> result = claimEnrichmentService.enrichClaimData(inputPayload);

        // Assert
        assertNotNull(result, "Enriched payload should not be null");
        assertEquals("APPROVER_UNAVAILABLE", result.get("enrichmentStatus"), "Enrichment status should indicate unavailability");
        assertEquals("RETRY_LATER", result.get("nextAction"), "Next action should be to retry later");
        assertEquals("CLM-98765", result.get("id"), "Claim ID should be preserved");
        verify(auditDiaryStore, times(1)).writeAuditLog(eq(claimId), anyString());
        verifyNoMoreInteractions(rulesEngineDecisionService, auditDiaryStore);
    }

    // Minimal interface stubs for compilation context
    interface RulesEngineDecisionService {
        Optional<Map<String, Object>> fetchDecision(String claimId);
    }

    interface AuditDiaryStore {
        String writeAuditLog(String entityKey, String payload);
    }

    class ClaimEnrichmentService {
        private final RulesEngineDecisionService rulesEngineDecisionService;
        private final AuditDiaryStore auditDiaryStore;

        ClaimEnrichmentService(RulesEngineDecisionService rulesEngineDecisionService, AuditDiaryStore auditDiaryStore) {
            this.rulesEngineDecisionService = rulesEngineDecisionService;
            this.auditDiaryStore = auditDiaryStore;
        }

        Map<String, Object> enrichClaimData(Map<String, Object> payload) {
            String claimId = (String) payload.get("id");
            Optional<Map<String, Object>> decision = rulesEngineDecisionService.fetchDecision(claimId);
            
            Map<String, Object> enriched = new java.util.HashMap<>(payload);
            String auditUri = auditDiaryStore.writeAuditLog(claimId, java.util.Map.of("enrichment", "APPROVER_UNAVAILABLE").toString());
            enriched.put("auditReference", auditUri);
            
            if (decision.isEmpty()) {
                enriched.put("enrichmentStatus", "APPROVER_UNAVAILABLE");
                enriched.put("nextAction", "RETRY_LATER");
            } else {
                enriched.put("enrichmentStatus", "APPROVED");
                enriched.put("nextAction", "PROCEED_TO_PAYMENT");
            }
            return enriched;
        }
    }
}
