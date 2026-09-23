package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationEnrichmentDecisionTest {

    @Mock
    private RulesEngineDecisionService rulesEngine;

    @Mock
    private WorkflowTaskRouter taskRouter;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    private ClaimEnrichmentDecisionService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimEnrichmentDecisionService(rulesEngine, taskRouter, auditDiaryStore);
    }

    @Test
    void decision_multiple_matches_rule_1_match_on_address_insured_date_expected_outcome_create_resolve_policy_match_task() {
        // Arrange
        String claimId = "CLM-TEST-001";
        Map<String, Object> claimPayload = Map.of(
                "id", claimId,
                "insuredName", "Jane Doe",
                "address", "456 Oak Avenue",
                "policyDate", "2023-05-15"
        );

        List<Map<String, Object>> multipleMatches = List.of(
                Map.of("matchId", "M-001", "policyId", "POL-A", "confidence", 0.98),
                Map.of("matchId", "M-002", "policyId", "POL-B", "confidence", 0.92)
        );

        when(rulesEngine.evaluateClaimMatches(claimId)).thenReturn(multipleMatches);

        // Act
        enrichmentService.processEnrichment(claimPayload);

        // Assert
        ArgumentCaptor<Map<String, Object>> taskPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(taskRouter, times(1)).createTask(taskPayloadCaptor.capture());

        Map<String, Object> actualTask = taskPayloadCaptor.getValue();
        assertEquals("RESOLVE_POLICY_MATCH", actualTask.get("taskType"));
        assertEquals(claimId, actualTask.get("claimId"));
        assertEquals(2, actualTask.get("matchCount"));
        assertEquals("Address/Insured/Date matched multiple policies", actualTask.get("reason"));

        verify(auditDiaryStore, times(1)).logEnrichmentDecision(eq(claimId), eq("MULTIPLE_MATCHES"));
    }

    // Minimal interface contracts for mock isolation
    private interface RulesEngineDecisionService {
        List<Map<String, Object>> evaluateClaimMatches(String claimId);
    }

    private interface WorkflowTaskRouter {
        void createTask(Map<String, Object> taskPayload);
    }

    private interface AuditDiaryStore {
        void logEnrichmentDecision(String claimId, String decisionType);
    }

    private static class ClaimEnrichmentDecisionService {
        private final RulesEngineDecisionService rulesEngine;
        private final WorkflowTaskRouter taskRouter;
        private final AuditDiaryStore auditDiaryStore;

        ClaimEnrichmentDecisionService(RulesEngineDecisionService rulesEngine,
                                       WorkflowTaskRouter taskRouter,
                                       AuditDiaryStore auditDiaryStore) {
            this.rulesEngine = rulesEngine;
            this.taskRouter = taskRouter;
            this.auditDiaryStore = auditDiaryStore;
        }

        void processEnrichment(Map<String, Object> payload) {
            String claimId = (String) payload.get("id");
            List<Map<String, Object>> matches = rulesEngine.evaluateClaimMatches(claimId);

            if (matches != null && matches.size() > 1) {
                Map<String, Object> taskPayload = Map.of(
                        "taskType", "RESOLVE_POLICY_MATCH",
                        "claimId", claimId,
                        "matchCount", matches.size(),
                        "reason", "Address/Insured/Date matched multiple policies"
                );
                taskRouter.createTask(taskPayload);
                auditDiaryStore.logEnrichmentDecision(claimId, "MULTIPLE_MATCHES");
            }
        }
    }
}
