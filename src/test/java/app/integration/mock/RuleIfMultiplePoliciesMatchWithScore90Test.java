package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock tests for Claim Data Standardization: Decision Transformation.
 * Verifies rule logic for policy matching scenarios under NewCo Insurance profile.
 */
public class ClaimDataStandardizationDecisionTransformationMockTest {

    /**
     * Infrastructure interfaces representing external contracts.
     */
    interface RulesEngineDecisionService {
        List<Map<String, Object>> evaluate(String claimId);
    }

    interface WorkflowTaskRouter {
        void createTask(String claimId, String taskType, Map<String, Object> context);
    }

    interface AuditDiaryStore {
        void write(String bucketName, String objectKey, String content);
    }

    /**
     * System Under Test: Decision Transformation Logic.
     * In a real project, this would reside in src/main/java.
     */
    class ClaimStandardizationDecisionService {
        private final RulesEngineDecisionService rulesEngine;
        private final WorkflowTaskRouter taskRouter;
        private final AuditDiaryStore auditStore;

        ClaimStandardizationDecisionService(RulesEngineDecisionService rulesEngine,
                                            WorkflowTaskRouter taskRouter,
                                            AuditDiaryStore auditStore) {
            this.rulesEngine = rulesEngine;
            this.taskRouter = taskRouter;
            this.auditStore = auditStore;
        }

        Map<String, Object> transform(String claimId, Map<String, Object> payload) {
            List<Map<String, Object>> matches = rulesEngine.evaluate(claimId);
            
            // Rule: If multiple policies match with score > 90
            boolean multipleHighScore = matches.size() > 1 && matches.stream()
                .allMatch(m -> ((Number) m.get("score")).doubleValue() > 90);

            if (multipleHighScore) {
                Map<String, Object> context = Map.of("claimId", claimId, "matchCount", matches.size());
                taskRouter.createTask(claimId, "Resolve Policy Match", context);
                // Audit logging for observability and compliance
                auditStore.write("AuditDiaryStore-bucket", "AuditDiaryStore/" + claimId + ".json", "TaskCreated");
            }
            return payload;
        }
    }

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    private ClaimStandardizationDecisionService sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sut = new ClaimStandardizationDecisionService(rulesEngineService, workflowTaskRouter, auditDiaryStore);
    }

    @Test
    void rule_if_multiple_policies_match_with_score_90_create_task_resolve_policy_match_n() {
        // Arrange
        String claimId = "CLM-90-RULE-TEST";
        List<Map<String, Object>> highScoreMatches = List.of(
            Map.of("policyId", "POL-A", "score", 92.0),
            Map.of("policyId", "POL-B", "score", 95.5)
        );

        when(rulesEngineService.evaluate(claimId)).thenReturn(highScoreMatches);

        // Act
        sut.transform(claimId, Map.of("claimId", claimId));

        // Assert: Verify task creation
        ArgumentCaptor<String> taskTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(workflowTaskRouter).createTask(eq(claimId), taskTypeCaptor.capture(), any());
        assertEquals("Resolve Policy Match", taskTypeCaptor.getValue());

        // Assert: Verify audit log write (NFR: Observability/Compliance)
        verify(auditDiaryStore).write(eq("AuditDiaryStore-bucket"), eq("AuditDiaryStore/" + claimId + ".json"), anyString());
        
        // Assert: Verify rules engine was called
        verify(rulesEngineService).evaluate(claimId);
    }
}
