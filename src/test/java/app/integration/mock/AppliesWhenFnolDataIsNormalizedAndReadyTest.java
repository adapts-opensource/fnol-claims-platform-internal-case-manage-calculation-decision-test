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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationCalculationTransformIntegrationMockTest {

    // Infra I/O Contract Mocks matching logical names
    interface AuditDiaryStore_s3 {
        String write(String bucketName, String objectKeyPattern);
    }

    interface RulesEngineDecisionService_dynamodb {
        Map<String, Object> queryItem(String tableName, String partitionKey);
    }

    interface WorkflowTaskRouter_dynamodb {
        String routeTask(String tableName, String partitionKey);
    }

    // Data Model
    static class ClaimDataStandardizationCalculationTransform {
        private final String id;
        private final Map<String, Object> payload;

        public ClaimDataStandardizationCalculationTransform(String id, Map<String, Object> payload) {
            this.id = id;
            this.payload = new HashMap<>(payload);
        }

        public String getId() { return id; }
        public Map<String, Object> getPayload() { return payload; }
    }

    // Service under test
    static class ClaimDataStandardizationCalculationTransformService {
        private final AuditDiaryStore_s3 auditDiaryStore;
        private final RulesEngineDecisionService_dynamodb rulesEngine;
        private final WorkflowTaskRouter_dynamodb taskRouter;

        public ClaimDataStandardizationCalculationTransformService(
                AuditDiaryStore_s3 auditDiaryStore,
                RulesEngineDecisionService_dynamodb rulesEngine,
                WorkflowTaskRouter_dynamodb taskRouter) {
            this.auditDiaryStore = auditDiaryStore;
            this.rulesEngine = rulesEngine;
            this.taskRouter = taskRouter;
        }

        public ClaimDataStandardizationCalculationTransform processAndValidate(ClaimDataStandardizationCalculationTransform input) {
            if (input.getPayload().get("status") != null && "NORMALIZED".equals(input.getPayload().get("status"))) {
                auditDiaryStore.write("AuditDiaryStore-bucket", "AuditDiaryStore/" + input.getId() + ".json");
                rulesEngine.queryItem("RulesEngineDecisionService_table", input.getId());
                taskRouter.routeTask("WorkflowTaskRouter_table", input.getId());
                input.getPayload().put("readyForMatching", true);
            }
            return input;
        }
    }

    @Mock
    private AuditDiaryStore_s3 auditDiaryStore;

    @Mock
    private RulesEngineDecisionService_dynamodb rulesEngine;

    @Mock
    private WorkflowTaskRouter_dynamodb taskRouter;

    @InjectMocks
    private ClaimDataStandardizationCalculationTransformService service;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles field injection automatically
    }

    @Test
    void applies_when_fnol_data_is_normalized_and_ready_for_matching() {
        // Arrange
        String claimId = "FNOL-456";
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "NORMALIZED");
        payload.put("fnolData", Map.of("policyNumber", "POL-999", "incidentType", "COLLISION"));

        ClaimDataStandardizationCalculationTransform input = new ClaimDataStandardizationCalculationTransform(claimId, payload);

        when(auditDiaryStore.write(anyString(), anyString())).thenReturn("s3://AuditDiaryStore-bucket/AuditDiaryStore/FNOL-456.json");
        when(rulesEngine.queryItem(anyString(), anyString())).thenReturn(Map.of("decision", "MATCH_ELIGIBLE"));
        when(taskRouter.routeTask(anyString(), anyString())).thenReturn("TASK-ROUTED-789");

        // Act
        ClaimDataStandardizationCalculationTransform result = service.processAndValidate(input);

        // Assert
        assertNotNull(result);
        assertEquals("NORMALIZED", result.getPayload().get("status"));
        assertTrue((Boolean) result.getPayload().get("readyForMatching"));
        
        verify(auditDiaryStore).write("AuditDiaryStore-bucket", "AuditDiaryStore/" + claimId + ".json");
        verify(rulesEngine).queryItem("RulesEngineDecisionService_table", claimId);
        verify(taskRouter).routeTask("WorkflowTaskRouter_table", claimId);
    }
}
