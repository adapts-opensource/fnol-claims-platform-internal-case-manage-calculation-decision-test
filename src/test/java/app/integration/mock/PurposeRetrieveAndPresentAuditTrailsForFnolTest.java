package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurposeRetrieveAndPresentAuditTrailsForFnolTest {

    @Mock
    private AuditDiaryStoreService auditDiaryStoreService;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouterService workflowTaskRouterService;

    private ClaimDataStandardizationCalculationTransformService service;

    @BeforeEach
    void setUp() {
        service = new ClaimDataStandardizationCalculationTransformService(
                auditDiaryStoreService,
                rulesEngineDecisionService,
                workflowTaskRouterService
        );
    }

    @Test
    void purpose_retrieve_and_present_audit_trails_for_fnol_decisions_with_explainability_context() {
        // Arrange
        String claimId = "fnol-claim-123";
        String bucketName = "AuditDiaryStore-bucket";
        String objectKey = "AuditDiaryStore/" + claimId + ".json";

        Map<String, Object> expectedAuditTrail = Map.of(
                "id", claimId,
                "payload", Map.of(
                        "decision", "APPROVED",
                        "explainability", "Standardized calculation applied based on policy rules v1.0",
                        "timestamp", System.currentTimeMillis()
                )
        );

        when(auditDiaryStoreService.readObject(bucketName, objectKey)).thenReturn(expectedAuditTrail);
        when(rulesEngineDecisionService.getItem("RulesEngineDecisionService_table", claimId)).thenReturn(Collections.emptyMap());
        when(workflowTaskRouterService.getItem("WorkflowTaskRouter_table", claimId)).thenReturn(Collections.emptyMap());

        // Act
        Map<String, Object> result = service.retrieveAndPresentAuditTrailsForFnol(claimId, bucketName);

        // Assert
        assertNotNull(result);
        assertEquals(claimId, result.get("id"));
        assertTrue(result.containsKey("payload"));
        Map<String, Object> payload = (Map<String, Object>) result.get("payload");
        assertTrue(payload.containsKey("explainability"));
        assertEquals("Standardized calculation applied based on policy rules v1.0", payload.get("explainability"));

        // Verify external I/O was called correctly (mocked, no live AWS/HTTP calls)
        verify(auditDiaryStoreService).readObject(bucketName, objectKey);
        verify(rulesEngineDecisionService).getItem("RulesEngineDecisionService_table", claimId);
        verify(workflowTaskRouterService).getItem("WorkflowTaskRouter_table", claimId);
    }

    // Minimal interfaces to support mocking without external dependencies
    interface AuditDiaryStoreService {
        Map<String, Object> readObject(String bucketName, String objectKey);
    }

    interface RulesEngineDecisionService {
        Map<String, Object> getItem(String tableName, String partitionKey);
    }

    interface WorkflowTaskRouterService {
        Map<String, Object> getItem(String tableName, String partitionKey);
    }

    // Service under test (simplified for mocking context)
    static class ClaimDataStandardizationCalculationTransformService {
        private final AuditDiaryStoreService auditDiaryStoreService;
        private final RulesEngineDecisionService rulesEngineDecisionService;
        private final WorkflowTaskRouterService workflowTaskRouterService;

        ClaimDataStandardizationCalculationTransformService(AuditDiaryStoreService auditDiaryStoreService,
                                                            RulesEngineDecisionService rulesEngineDecisionService,
                                                            WorkflowTaskRouterService workflowTaskRouterService) {
            this.auditDiaryStoreService = auditDiaryStoreService;
            this.rulesEngineDecisionService = rulesEngineDecisionService;
            this.workflowTaskRouterService = workflowTaskRouterService;
        }

        Map<String, Object> retrieveAndPresentAuditTrailsForFnol(String claimId, String bucketName) {
            String objectKey = "AuditDiaryStore/" + claimId + ".json";
            Map<String, Object> auditTrail = auditDiaryStoreService.readObject(bucketName, objectKey);
            rulesEngineDecisionService.getItem("RulesEngineDecisionService_table", claimId);
            workflowTaskRouterService.getItem("WorkflowTaskRouter_table", claimId);
            return auditTrail;
        }
    }
}
