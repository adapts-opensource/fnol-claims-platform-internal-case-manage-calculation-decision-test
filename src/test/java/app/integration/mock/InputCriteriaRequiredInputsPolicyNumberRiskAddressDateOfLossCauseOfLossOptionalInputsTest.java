package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Mock tests for Claim Data Standardization: decision transformation.
 * Verifies input validation rules for required/optional fields, non-empty constraints,
 * and date format validation while mocking external I/O contracts.
 */
public class ClaimDataStandardizationDecisionTransformationMockTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @InjectMocks
    private ClaimDataStandardizationDecisionTransformationService claimDataStandardizationDecisionTransformationService;

    private Map<String, Object> baseValidInput;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Initialize base valid input for reuse
        baseValidInput = new HashMap<>();
        baseValidInput.put("policy_number", "POL-2023-987654");
        baseValidInput.put("risk_address", "123 Insurance Blvd, Metropolis, NY 10001");
        baseValidInput.put("date_of_loss", LocalDate.now().minusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE));
        baseValidInput.put("cause_of_loss", "Vehicle Collision");
        
        // Mock external dependencies
        when(rulesEngineDecisionService.query(anyString())).thenReturn(Map.of("status", "ACTIVE"));
        when(workflowTaskRouter.routeTask(anyString(), anyString())).thenReturn(Map.of("task_id", "TASK-001"));
        when(auditDiaryStore.write(anyString(), anyString())).thenReturn("s3://audit-bucket/logs/audit-001.json");
    }

    /**
     * Validates the comprehensive input criteria including required fields, optional fields,
     * non-empty constraints, and date_of_loss validity.
     */
    @Test
    @DisplayName("Input Criteria: Required inputs, Optional inputs, Non-empty validation, Date validation")
    void input_criteria_required_inputs_policy_number_risk_address_date_of_loss_cause_of_loss_optional_inputs_catastrophe_event_reporter_damaged_area_prior_claim_status_input_validation_all_inputs_must_be_non_empty_if_provided_date_of_loss_must_be_valid() {
        // --- Scenario 1: All required and optional inputs provided with valid values ---
        Map<String, Object> fullValidInput = new HashMap<>(baseValidInput);
        fullValidInput.put("catastrophe_event", "Hurricane Season");
        fullValidInput.put("reporter", "Jane Doe");
        fullValidInput.put("damaged_area", "Front Bumper");
        fullValidInput.put("prior_claim_status", "No Prior Claims");

        var result = claimDataStandardizationDecisionTransformationService.transform(fullValidInput);
        assertNotNull(result, "Transformation should succeed with all valid inputs");
        assertEquals("POL-2023-987654", result.get("policy_number"), "Policy number should be preserved");
        
        // Verify external I/O was invoked correctly during transformation
        verify(rulesEngineDecisionService, times(1)).query(anyString());
        verify(workflowTaskRouter, times(1)).routeTask(anyString(), anyString());
        verify(auditDiaryStore, times(1)).write(anyString(), anyString());

        // --- Scenario 2: Missing required input (cause_of_loss) ---
        Map<String, Object> missingRequiredInput = new HashMap<>(baseValidInput);
        missingRequiredInput.remove("cause_of_loss");

        assertThrows(IllegalArgumentException.class, () -> {
            claimDataStandardizationDecisionTransformationService.transform(missingRequiredInput);
        }, "Transformation should fail when required input 'cause_of_loss' is missing");

        // --- Scenario 3: Optional input provided but empty (violates non-empty rule) ---
        Map<String, Object> emptyOptionalInput = new HashMap<>(fullValidInput);
        emptyOptionalInput.put("reporter", "");

        assertThrows(IllegalArgumentException.class, () -> {
            claimDataStandardizationDecisionTransformationService.transform(emptyOptionalInput);
        }, "Transformation should fail when optional input 'reporter' is provided but empty");

        // --- Scenario 4: Invalid date_of_loss format ---
        Map<String, Object> invalidDateInput = new HashMap<>(baseValidInput);
        invalidDateInput.put("date_of_loss", "not-a-valid-date");

        assertThrows(DateTimeParseException.class, () -> {
            claimDataStandardizationDecisionTransformationService.transform(invalidDateInput);
        }, "Transformation should fail when date_of_loss is not a valid date");

        // --- Scenario 5: Empty string for required input (violates non-empty rule) ---
        Map<String, Object> emptyRequiredInput = new HashMap<>(baseValidInput);
        emptyRequiredInput.put("policy_number", "   ");

        assertThrows(IllegalArgumentException.class, () -> {
            claimDataStandardizationDecisionTransformationService.transform(emptyRequiredInput);
        }, "Transformation should fail when required input 'policy_number' is whitespace/empty");
    }
}
