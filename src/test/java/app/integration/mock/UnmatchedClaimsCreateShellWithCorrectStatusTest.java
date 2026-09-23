package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnmatchedClaimsCreateShellWithCorrectStatusTest {

    @Mock
    private RulesEngineDecisionService rulesEngineService;
    @Mock
    private WorkflowTaskRouter workflowTaskRouter;
    @Mock
    private AuditDiaryStore auditDiaryStore;

    private DecisionTransformationHandler transformationHandler;

    @BeforeEach
    void setUp() {
        transformationHandler = new DecisionTransformationHandler(rulesEngineService, workflowTaskRouter, auditDiaryStore);
    }

    @Test
    void unmatched_claims_create_shell_with_correct_status() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> claimPayload = Map.of(
                "claim_id", claimId,
                "type", "FNOL",
                "standardization_status", "UNMATCHED"
        );

        when(rulesEngineService.fetchRules(anyString())).thenReturn(Map.of("rule_set", "STANDARD_V1"));
        when(workflowTaskRouter.createTask(any())).thenReturn(Map.of("task_id", "TASK-001"));
        when(auditDiaryStore.write(anyString(), anyString())).thenReturn("s3://AuditDiaryStore-bucket/" + claimId + ".json");

        // Act
        String shellId = transformationHandler.transformAndCreateShell(claimPayload);

        // Assert
        assertNotNull(shellId, "Shell ID should be generated for unmatched claims");
        assertFalse(shellId.isEmpty(), "Shell ID must not be empty");

        // Verify infrastructure interactions
        verify(rulesEngineService, times(1)).fetchRules(anyString());
        verify(workflowTaskRouter, times(1)).createTask(any());
        verify(auditDiaryStore, times(1)).write(anyString(), anyString());

        // Verify shell creation status via captor
        ArgumentCaptor<Map<String, Object>> shellPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(workflowTaskRouter, times(1)).createTask(shellPayloadCaptor.capture());
        Map<String, Object> capturedShell = shellPayloadCaptor.getValue();
        assertEquals("SHELL_CREATED", capturedShell.get("status"), "Shell status must be correctly set for unmatched claims");
        assertEquals(claimId, capturedShell.get("parent_claim_id"), "Shell must reference original claim ID");
    }

    // Infrastructure contract mocks to isolate external I/O
    interface RulesEngineDecisionService { Map<String, Object> fetchRules(String table); }
    interface WorkflowTaskRouter { Map<String, Object> createTask(Map<String, Object> payload); }
    interface AuditDiaryStore { String write(String bucket, String key); }

    // Service under test
    class DecisionTransformationHandler {
        private final RulesEngineDecisionService rulesEngineService;
        private final WorkflowTaskRouter workflowTaskRouter;
        private final AuditDiaryStore auditDiaryStore;

        DecisionTransformationHandler(RulesEngineDecisionService rulesEngineService,
                                      WorkflowTaskRouter workflowTaskRouter,
                                      AuditDiaryStore auditDiaryStore) {
            this.rulesEngineService = rulesEngineService;
            this.workflowTaskRouter = workflowTaskRouter;
            this.auditDiaryStore = auditDiaryStore;
        }

        String transformAndCreateShell(Map<String, Object> payload) {
            String claimId = String.valueOf(payload.get("claim_id"));
            String status = String.valueOf(payload.get("standardization_status"));

            if ("UNMATCHED".equalsIgnoreCase(status)) {
                rulesEngineService.fetchRules("RulesEngineDecisionService_table");
                Map<String, Object> shellPayload = Map.of(
                        "parent_claim_id", claimId,
                        "status", "SHELL_CREATED",
                        "type", "SHELL"
                );
                workflowTaskRouter.createTask(shellPayload);
                auditDiaryStore.write("AuditDiaryStore-bucket", "AuditDiaryStore/" + claimId + ".json");
                return UUID.randomUUID().toString();
            }
            throw new IllegalStateException("Expected unmatched status");
        }
    }
}
