package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppliesWhenUserSubmitsFnolFormDataTest {

    @Mock
    private ClaimTransformationService claimTransformationService;

    @Mock
    private AuditDiaryStoreService auditDiaryStoreService;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouterService workflowTaskRouterService;

    private ClaimDataStandardizationCalculationTransformHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ClaimDataStandardizationCalculationTransformHandler(
                claimTransformationService,
                auditDiaryStoreService,
                rulesEngineDecisionService,
                workflowTaskRouterService
        );
    }

    @Test
    void applies_when_user_submits_fnol_form_data() {
        // Arrange: Simulate FNOL form data submission
        Map<String, Object> fnolFormData = new HashMap<>();
        fnolFormData.put("claimId", "FNOL-2024-001");
        fnolFormData.put("policyNumber", "POL-8821");
        fnolFormData.put("incidentDate", "2024-05-15T10:00:00Z");
        fnolFormData.put("lossType", "COLLISION");
        fnolFormData.put("estimatedDamageAmount", 3500.0);

        Map<String, Object> transformedPayload = new HashMap<>();
        transformedPayload.put("id", "FNOL-2024-001");
        transformedPayload.put("payload", fnolFormData);
        transformedPayload.put("standardizedSchemaVersion", "1.0");
        transformedPayload.put("calculationState", "TRANSFORMED");

        when(claimTransformationService.transform(fnolFormData)).thenReturn(transformedPayload);
        doNothing().when(auditDiaryStoreService).writeToS3(anyString(), anyString());
        when(rulesEngineDecisionService.queryDynamoDb(anyString())).thenReturn(Map.of("ruleResult", "APPROVED"));
        doNothing().when(workflowTaskRouterService).routeToDynamoDb(anyString(), anyString());

        // Act: Process the FNOL submission
        Map<String, Object> result = handler.processFnolSubmission(fnolFormData);

        // Assert: Verify transformation and side-effects
        assertNotNull(result, "Transformed result should not be null");
        assertEquals("FNOL-2024-001", result.get("id"), "ID should match submitted claimId");
        assertEquals(fnolFormData, result.get("payload"), "Payload should contain original form data");
        assertEquals("TRANSFORMED", result.get("calculationState"), "Calculation state should be updated");

        verify(claimTransformationService, times(1)).transform(fnolFormData);
        verify(auditDiaryStoreService, times(1)).writeToS3(eq("AuditDiaryStore-bucket"), eq("AuditDiaryStore/FNOL-2024-001.json"));
        verify(rulesEngineDecisionService, times(1)).queryDynamoDb(eq("RulesEngineDecisionService_table"));
        verify(workflowTaskRouterService, times(1)).routeToDynamoDb(eq("WorkflowTaskRouter_table"), eq("CLAIM_INTAKE_QUEUE"));
    }
}
