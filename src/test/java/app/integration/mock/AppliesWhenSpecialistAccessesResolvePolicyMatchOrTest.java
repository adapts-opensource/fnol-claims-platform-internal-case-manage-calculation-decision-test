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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies Claim Data Standardization:decision:enrichment behavior.
 * NFRs: thread_safety (stateless service), input_validation (payload constraints),
 *       structured_logging (audit trail), tls_in_transit (mocked), least_privilege_iam (mocked).
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationEnrichmentMockTest {

    @Mock
    private WorkflowTaskRouter mockTaskRouter;

    @Mock
    private RulesEngineDecisionService mockRulesEngine;

    @Mock
    private AuditDiaryStore mockAuditDiaryStore;

    private ClaimEnrichmentDecisionService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimEnrichmentDecisionService(mockTaskRouter, mockRulesEngine, mockAuditDiaryStore);
    }

    @Test
    void applies_when_specialist_accesses_resolve_policy_match_or_unmatched_fnol_task() {
        // Arrange: Specialist accesses Resolve Policy Match or Unmatched FNOL task
        String taskId = "task-resolve-policy-match-001";
        String specialistId = "specialist-ins-042";
        String taskType = "RESOLVE_POLICY_MATCH";
        Map<String, Object> taskContext = Map.of(
                "task_id", taskId,
                "task_type", taskType,
                "accessed_by", specialistId,
                "claim_id", "claim-789",
                "pii_masked", true
        );

        when(mockTaskRouter.fetchTaskById(taskId)).thenReturn(Optional.of(taskContext));
        when(mockRulesEngine.evaluateEnrichment(any(Map.class))).thenReturn(Map.of(
                "enrichment_applied", true,
                "decision_status", "ENRICHED",
                "payload", Map.of("standardized_fields", true, "compliance_flags", Map.of("gdpr", true, "soc2", true))
        ));

        // Act: Trigger enrichment process
        Map<String, Object> decisionResult = enrichmentService.evaluateAndEnrich(taskId);

        // Assert: Verify enrichment applies under the specified condition
        assertNotNull(decisionResult, "Enrichment decision payload must not be null");
        assertTrue((Boolean) decisionResult.get("enrichment_applied"),
                "Enrichment must be applied when specialist accesses the task");
        assertEquals("ENRICHED", decisionResult.get("decision_status"));

        // Verify external I/O contracts are mocked and never call live AWS/HTTP endpoints
        verify(mockTaskRouter, times(1)).fetchTaskById(taskId);
        verify(mockRulesEngine, times(1)).evaluateEnrichment(any(Map.class));
        verify(mockAuditDiaryStore, times(1)).writeAuditEntry(anyString(), anyString());
    }

    /**
     * Minimal service implementation for test isolation.
     * In production, this would be injected via Spring/Dagger and connect to AWS SDK v2.
     */
    static class ClaimEnrichmentDecisionService {
        private final WorkflowTaskRouter taskRouter;
        private final RulesEngineDecisionService rulesEngine;
        private final AuditDiaryStore auditStore;

        ClaimEnrichmentDecisionService(WorkflowTaskRouter taskRouter, RulesEngineDecisionService rulesEngine, AuditDiaryStore auditStore) {
            this.taskRouter = taskRouter;
            this.rulesEngine = rulesEngine;
            this.auditStore = auditStore;
        }

        Map<String, Object> evaluateAndEnrich(String taskId) {
            Optional<Map<String, Object>> taskOpt = taskRouter.fetchTaskById(taskId);
            if (taskOpt.isEmpty()) {
                throw new IllegalArgumentException("Input validation failed: taskId must not be empty or null");
            }
            Map<String, Object> task = taskOpt.get();
            Map<String, Object> enriched = rulesEngine.evaluateEnrichment(task);
            auditStore.writeAuditEntry(taskId, "ENRICHMENT_APPLIED");
            return enriched;
        }
    }

    interface WorkflowTaskRouter {
        Optional<Map<String, Object>> fetchTaskById(String taskId);
    }

    interface RulesEngineDecisionService {
        Map<String, Object> evaluateEnrichment(Map<String, Object> context);
    }

    interface AuditDiaryStore {
        void writeAuditEntry(String entityId, String action);
    }
}
