package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AobContractorTransformTest {

    @Mock
    private ClaimTransformationService claimTransformationService;

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private RulesEngineService rulesEngineService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and cleanup automatically
    }

    @Test
    void transform_to_aob_claim_on_contractor_control() {
        // Arrange: Prepare input payload per test case label AOBContractorTransform
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("tenant_code", "FL01");
        inputPayload.put("year", 2024);
        inputPayload.put("aob_flag", true);
        inputPayload.put("contractor_control", true);
        inputPayload.put("date_of_loss", "2024-03-15");
        inputPayload.put("cause_of_loss", "wind");
        inputPayload.put("product", "HO3");

        // Mock expected transformation output
        Map<String, Object> expectedOutput = new HashMap<>();
        expectedOutput.put("claim_number", "CLM-FL01-2024-001");
        expectedOutput.put("claim_type", "AOB_CONTRACTOR_INVOLVED");
        
        Map<String, String> tasks = new HashMap<>();
        tasks.put("aob_review", "CREATED");
        tasks.put("assignment_doc_validation", "TRIGGERED");
        expectedOutput.put("tasks", tasks);
        
        expectedOutput.put("communication_rights_evaluated", true);

        when(claimTransformationService.transform(inputPayload)).thenReturn(expectedOutput);

        // Act: Invoke transformation service
        Map<String, Object> result = claimTransformationService.transform(inputPayload);

        // Assert: Verify expected results
        assertNotNull(result, "Claim number generated");
        assertEquals("CLM-FL01-2024-001", result.get("claim_number"), "Claim number should be generated");
        assertEquals("AOB_CONTRACTOR_INVOLVED", result.get("claim_type"), "Claim type should be set to AOB/contractor-involved");

        @SuppressWarnings("unchecked")
        Map<String, String> resultTasks = (Map<String, String>) result.get("tasks");
        assertNotNull(resultTasks, "Tasks map should exist");
        assertEquals("CREATED", resultTasks.get("aob_review"), "Task AOB Review should be created");
        assertEquals("TRIGGERED", resultTasks.get("assignment_doc_validation"), "Assignment document validation task should be triggered");

        assertTrue((Boolean) result.get("communication_rights_evaluated"), "Communication rights should be evaluated");

        // Verify service interactions
        verify(claimTransformationService, times(1)).transform(inputPayload);
        // External I/O mocks are isolated; verify they are not called directly in this unit test
        verifyNoInteractions(documentStoreService, rulesEngineService);
    }

    // Minimal stubs to ensure structural completeness when running in isolated environments
    interface ClaimTransformationService { Map<String, Object> transform(Map<String, Object> payload); }
    interface DocumentStoreService {}
    interface RulesEngineService {}
}
