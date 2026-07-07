package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionTransformationMockTest {

    @Mock
    private AuditDiaryStore mockAuditDiaryStore;
    @Mock
    private RulesEngineDecisionService mockRulesEngine;
    @Mock
    private WorkflowTaskRouter mockWorkflowTaskRouter;

    @InjectMocks
    private ClaimStandardizationDecisionService claimService;

    private Map<String, Object> basePayload;

    @BeforeEach
    void setUp() {
        basePayload = new HashMap<>();
        basePayload.put("id", "CLM-1001");
        basePayload.put("lossDate", LocalDate.of(2023, 1, 10).format(DateTimeFormatter.ISO_LOCAL_DATE));
        basePayload.put("effectiveDate", LocalDate.of(2023, 1, 15).format(DateTimeFormatter.ISO_LOCAL_DATE));
        basePayload.put("gracePeriodApplies", false);
    }

    @Test
    void ruleIfLossBeforeEffectiveClaimIsInvalidUnlessGracePeriodAppliesN() {
        // Arrange
        Map<String, Object> inputPayload = new HashMap<>(basePayload);
        
        // Mock external I/O contracts per infra_io_contracts
        when(mockAuditDiaryStore.store(anyString(), anyString())).thenReturn("s3://AuditDiaryStore-bucket/CLM-1001.json");
        when(mockRulesEngine.query(anyString(), anyString())).thenReturn(Map.of("engineStatus", "ACTIVE"));
        when(mockWorkflowTaskRouter.route(anyString(), anyMap())).thenReturn("ROUTED_TASK");

        // Act
        Map<String, Object> transformedPayload = claimService.transformDecision(inputPayload);

        // Assert
        assertEquals("INVALID", transformedPayload.get("claimStatus"),
                "Claim must be marked INVALID when lossDate < effectiveDate and grace period does not apply");
        assertEquals("CLM-1001", transformedPayload.get("id"));
        assertFalse((Boolean) transformedPayload.get("gracePeriodApplies"));

        // Verify external I/O interactions
        verify(mockAuditDiaryStore, times(1)).store(eq("AuditDiaryStore-bucket"), anyString());
        verify(mockRulesEngine, times(1)).query(eq("RulesEngineDecisionService_table"), anyString());
        verify(mockWorkflowTaskRouter, times(1)).route(anyString(), anyMap());
    }

    // Minimal infra contract interfaces for compilation
    interface AuditDiaryStore {
        String store(String bucketName, String objectKeyPattern);
    }

    interface RulesEngineDecisionService {
        Map<String, Object> query(String tableName, String partitionKey);
    }

    interface WorkflowTaskRouter {
        String route(String taskId, Map<String, Object> payload);
    }

    // Service implementing the decision transformation rule
    static class ClaimStandardizationDecisionService {
        private final AuditDiaryStore auditDiaryStore;
        private final RulesEngineDecisionService rulesEngine;
        private final WorkflowTaskRouter workflowTaskRouter;

        ClaimStandardizationDecisionService(AuditDiaryStore auditDiaryStore,
                                            RulesEngineDecisionService rulesEngine,
                                            WorkflowTaskRouter workflowTaskRouter) {
            this.auditDiaryStore = auditDiaryStore;
            this.rulesEngine = rulesEngine;
            this.workflowTaskRouter = workflowTaskRouter;
        }

        Map<String, Object> transformDecision(Map<String, Object> payload) {
            String claimId = (String) payload.get("id");
            String lossDateStr = (String) payload.get("lossDate");
            String effectiveDateStr = (String) payload.get("effectiveDate");
            Boolean gracePeriodApplies = (Boolean) payload.get("gracePeriodApplies");

            LocalDate lossDate = LocalDate.parse(lossDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate effectiveDate = LocalDate.parse(effectiveDateStr, DateTimeFormatter.ISO_LOCAL_DATE);

            // Rule: If loss before effective, claim is invalid unless grace period applies
            if (lossDate.isBefore(effectiveDate)) {
                payload.put("claimStatus", (gracePeriodApplies != null && gracePeriodApplies) ? "VALID" : "INVALID");
            } else {
                payload.put("claimStatus", "VALID");
            }

            // Simulate infra I/O calls per contract
            auditDiaryStore.store("AuditDiaryStore-bucket", "AuditDiaryStore/" + claimId + ".json");
            rulesEngine.query("RulesEngineDecisionService_table", "pk");
            workflowTaskRouter.route("task-1", payload);

            return payload;
        }
    }
}
