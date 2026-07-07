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
public class ClaimDataStandardizationDecisionEnrichmentMockTest {

    // Internal interfaces to represent external infra contracts for mocking
    interface RulesEngineDecisionService {
        Map<String, Object> evaluate(Map<String, Object> payload);
    }

    interface WorkflowTaskRouter {
        String route(Map<String, Object> payload);
    }

    interface AuditDiaryStore {
        void log(String entityId, Map<String, Object> entry);
    }

    // Component under test
    static class DecisionEnrichmentProcessor {
        private final RulesEngineDecisionService rulesEngine;
        private final WorkflowTaskRouter taskRouter;
        private final AuditDiaryStore auditStore;

        DecisionEnrichmentProcessor(RulesEngineDecisionService rulesEngine,
                                    WorkflowTaskRouter taskRouter,
                                    AuditDiaryStore auditStore) {
            this.rulesEngine = rulesEngine;
            this.taskRouter = taskRouter;
            this.auditStore = auditStore;
        }

        Map<String, Object> enrich(Map<String, Object> payload) {
            Map<String, Object> decision = rulesEngine.evaluate(payload);
            taskRouter.route(decision);
            auditStore.log((String) payload.get("id"), decision);
            return decision;
        }
    }

    @Mock
    private RulesEngineDecisionService rulesEngineMock;
    @Mock
    private WorkflowTaskRouter taskRouterMock;
    @Mock
    private AuditDiaryStore auditStoreMock;

    @InjectMocks
    private DecisionEnrichmentProcessor processor;

    @Test
    void output_criteria_success_outputs_resolved_policy_id_updated_coverage_flags_triage_path_audit_log_entry_failure_outputs_rejection_reason_corrected_task_id() {
        // Arrange
        Map<String, Object> inputPayload = Map.of(
            "id", "CLM-987",
            "policy_id", "POL-INIT",
            "coverage_flags", "BASIC",
            "task_id", "TASK-INIT"
        );

        Map<String, Object> expectedSuccessOutputs = Map.of(
            "resolved_policy_id", "POL-STANDARDIZED",
            "updated_coverage_flags", "BASIC,PREMIUM",
            "triage_path", "AUTO_APPROVE",
            "audit_log_entry", "ENRICHMENT_COMPLETED"
        );

        when(rulesEngineMock.evaluate(any())).thenReturn(expectedSuccessOutputs);
        when(taskRouterMock.route(any())).thenReturn("ROUTED");
        doNothing().when(auditStoreMock).log(anyString(), anyMap());

        // Act
        Map<String, Object> actualResult = processor.enrich(inputPayload);

        // Assert success outputs
        assertNotNull(actualResult);
        assertEquals("POL-STANDARDIZED", actualResult.get("resolved_policy_id"));
        assertEquals("BASIC,PREMIUM", actualResult.get("updated_coverage_flags"));
        assertEquals("AUTO_APPROVE", actualResult.get("triage_path"));
        assertEquals("ENRICHMENT_COMPLETED", actualResult.get("audit_log_entry"));

        // Assert failure outputs are absent in success path
        assertNull(actualResult.get("rejection_reason"));
        assertNull(actualResult.get("corrected_task_id"));

        // Verify infra calls
        verify(rulesEngineMock).evaluate(inputPayload);
        verify(taskRouterMock).route(expectedSuccessOutputs);
        verify(auditStoreMock).log("CLM-987", expectedSuccessOutputs);
    }
}
