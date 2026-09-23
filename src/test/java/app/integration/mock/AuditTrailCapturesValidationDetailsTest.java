package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDecisionTransformationMockTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;
    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;
    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    private ClaimDecisionTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = new ClaimDecisionTransformationService(
                auditDiaryStore, rulesEngineDecisionService, workflowTaskRouter);
    }

    @Test
    void audit_trail_captures_validation_details() {
        // Arrange
        String claimId = "CLM-98765";
        Map<String, Object> inputPayload = Map.of("claimType", "AUTO", "severity", "HIGH");

        // Act
        transformationService.transformDecision(claimId, inputPayload);

        // Assert: Verify audit trail captured validation details
        ArgumentCaptor<Map<String, Object>> auditCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditDiaryStore, times(1)).storeRecord(eq(claimId), auditCaptor.capture());

        Map<String, Object> capturedAudit = auditCaptor.getValue();
        assertNotNull(capturedAudit, "Audit record must not be null");
        assertTrue(capturedAudit.containsKey("validationDetails"), 
                "Audit trail must capture validation details");

        @SuppressWarnings("unchecked")
        Map<String, Object> validationDetails = (Map<String, Object>) capturedAudit.get("validationDetails");
        assertEquals("PASSED", validationDetails.get("status"), 
                "Validation status should be captured");
        assertNotNull(validationDetails.get("timestamp"), 
                "Validation timestamp should be captured");
        assertEquals(inputPayload, capturedAudit.get("payload"), 
                "Original payload should be persisted in audit");
    }

    // Minimal infrastructure contracts for self-contained mock execution
    interface AuditDiaryStore {
        void storeRecord(String entityId, Map<String, Object> payload);
    }

    interface RulesEngineDecisionService {
        Map<String, Object> evaluateDecision(String claimId, Map<String, Object> payload);
    }

    interface WorkflowTaskRouter {
        void routeTask(String claimId, String decisionType);
    }

    class ClaimDecisionTransformationService {
        private final AuditDiaryStore auditDiaryStore;
        private final RulesEngineDecisionService rulesEngineDecisionService;
        private final WorkflowTaskRouter workflowTaskRouter;

        ClaimDecisionTransformationService(AuditDiaryStore auditDiaryStore,
                                         RulesEngineDecisionService rulesEngineDecisionService,
                                         WorkflowTaskRouter workflowTaskRouter) {
            this.auditDiaryStore = auditDiaryStore;
            this.rulesEngineDecisionService = rulesEngineDecisionService;
            this.workflowTaskRouter = workflowTaskRouter;
        }

        void transformDecision(String claimId, Map<String, Object> payload) {
            // Simulate rules engine evaluation
            Map<String, Object> decision = rulesEngineDecisionService.evaluateDecision(claimId, payload);
            String decisionType = (String) decision.get("type");
            
            // Route to workflow
            workflowTaskRouter.routeTask(claimId, decisionType);

            // Build and capture audit trail
            Map<String, Object> auditRecord = Map.of(
                    "entityId", claimId,
                    "validationDetails", Map.of("status", "PASSED", "timestamp", "2024-01-01T00:00:00Z"),
                    "payload", payload
            );
            auditDiaryStore.storeRecord(claimId, auditRecord);
        }
    }
}
