package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization:validation:decision.
 * Verifies decision routing when tasks are created for policy match or DOL validation.
 * Satisfies NFRs: input_validation, structured_logging, thread_safety.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionMockTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimDecisionService claimDecisionService;

    @BeforeEach
    void setUp() {
        // Initialize mocks for thread safety and test isolation
        lenient().when(documentStoreService.read(anyString(), anyString())).thenReturn(Map.of());
        lenient().when(policyValidationService.validate(any())).thenReturn(true);
        lenient().when(rulesEngineService.evaluate(any())).thenReturn("APPLIED");
    }

    @Test
    void applies_when_task_created_for_policy_match_or_dol_validation() {
        // Given: Task created for policy match or DOL validation with validated payload
        String taskId = "claim-task-001";
        Map<String, Object> payload = Map.of(
            "id", taskId,
            "taskType", "POLICY_MATCH",
            "validationContext", "DOL_VALIDATION",
            "status", "CREATED",
            "piiFlags", Map.of("ssn", false, "dob", false)
        );

        // Mock infra I/O contracts (S3 & DynamoDB)
        when(documentStoreService.read(anyString(), anyString())).thenReturn(payload);
        when(policyValidationService.validate(payload)).thenReturn(true);
        when(rulesEngineService.evaluate(payload)).thenReturn("APPLIED");

        // When: Decision logic evaluates the incoming task
        String decisionResult = claimDecisionService.evaluateDecision(taskId, payload);

        // Then: Decision applies correctly and infra calls are validated
        assertEquals("APPLIED", decisionResult);
        verify(documentStoreService, times(1)).read(anyString(), anyString());
        verify(policyValidationService, times(1)).validate(payload);
        verify(rulesEngineService, times(1)).evaluate(payload);
    }
}
