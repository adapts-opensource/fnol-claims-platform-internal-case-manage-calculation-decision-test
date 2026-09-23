package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// Minimal domain/infra interfaces to support mock compilation
interface ClaimTransformationEngine {
    Map<String, Object> process(Map<String, Object> payload);
}

interface AuditDiaryStore {
    void write(String bucketName, String objectKeyPattern, Map<String, Object> data);
}

interface RulesEngineDecisionService {
    void storeDecision(String tableName, Map<String, Object> decisionPayload);
}

interface WorkflowTaskRouter {
    void routeTask(String tableName, Map<String, Object> taskPayload);
}

class ClaimDataStandardizationDecisionTransformer {
    private final ClaimTransformationEngine engine;
    private final AuditDiaryStore auditStore;
    private final RulesEngineDecisionService rulesService;
    private final WorkflowTaskRouter taskRouter;

    ClaimDataStandardizationDecisionTransformer(
            ClaimTransformationEngine engine,
            AuditDiaryStore auditStore,
            RulesEngineDecisionService rulesService,
            WorkflowTaskRouter taskRouter) {
        this.engine = engine;
        this.auditStore = auditStore;
        this.rulesService = rulesService;
        this.taskRouter = taskRouter;
    }

    Map<String, Object> applyDecisionTransformation(Map<String, Object> fnolIntakeData) {
        String policyId = String.valueOf(fnolIntakeData.getOrDefault("policyIdentifier", ""));
        String riskAddr = String.valueOf(fnolIntakeData.getOrDefault("riskAddress", ""));

        if (policyId.isEmpty() && riskAddr.isEmpty()) {
            throw new IllegalArgumentException("FNOL data must contain at least one policy identifier or risk address");
        }

        Map<String, Object> transformed = new HashMap<>();
        transformed.put("id", "TRANSFORMED-" + System.currentTimeMillis());
        transformed.put("payload", fnolIntakeData);

        engine.process(transformed);
        auditStore.write("AuditDiaryStore-bucket", "AuditDiaryStore/{id}.json", transformed);
        rulesService.storeDecision("RulesEngineDecisionService_table", transformed);
        taskRouter.routeTask("WorkflowTaskRouter_table", transformed);

        return transformed;
    }
}

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionTransformationTest {

    @Mock
    private ClaimTransformationEngine transformationEngine;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    private ClaimDataStandardizationDecisionTransformer transformer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        transformer = new ClaimDataStandardizationDecisionTransformer(
                transformationEngine, auditDiaryStore, rulesEngineDecisionService, workflowTaskRouter
        );
    }

    @Test
    void applies_when_fnol_intake_data_contains_at_least_one_policy_identifier_or_risk_address() {
        // Given: FNOL intake data contains at least one policy identifier or risk address
        Map<String, Object> fnolIntakePayload = new HashMap<>();
        fnolIntakePayload.put("policyIdentifier", "POL-ABC-123");
        fnolIntakePayload.put("claimReference", "REF-999");

        Map<String, Object> expectedStandardizedPayload = new HashMap<>();
        expectedStandardizedPayload.put("id", "TRANSFORMED-001");
        expectedStandardizedPayload.put("payload", fnolIntakePayload);

        when(transformationEngine.process(any(Map.class))).thenReturn(expectedStandardizedPayload);

        // When
        Map<String, Object> result = transformer.applyDecisionTransformation(fnolIntakePayload);

        // Then
        assertNotNull(result, "Transformation should apply and return a result");
        assertEquals("TRANSFORMED-001", result.get("id"));
        assertEquals(fnolIntakePayload, result.get("payload"));

        // Verify infra I/O contracts were invoked per contract specs
        verify(transformationEngine, times(1)).process(any(Map.class));
        verify(auditDiaryStore, times(1)).write(anyString(), anyString(), any(Map.class));
        verify(rulesEngineDecisionService, times(1)).storeDecision(anyString(), any(Map.class));
        verify(workflowTaskRouter, times(1)).routeTask(anyString(), any(Map.class));
    }
}
