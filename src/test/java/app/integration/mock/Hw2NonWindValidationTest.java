package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

@DisplayName("Insured Engagement & Tracking: Transformation & Validation - Hw2NonWindValidation")
class InsuredEngagementTrackingTransformationValidationHw2NonWindValidationTest {

    private TransformationValidationHandler handler;
    private ValidationEngine validationEngine;
    private RoutingService routingService;
    private TaskGenerationService taskService;
    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        validationEngine = mock(ValidationEngine.class);
        routingService = mock(RoutingService.class);
        taskService = mock(TaskGenerationService.class);
        auditLogger = mock(AuditLogger.class);
        handler = new TransformationValidationHandler(validationEngine, routingService, taskService, auditLogger);
    }

    @Test
    @DisplayName("validate_hw2_non_wind_cause_triggers_coverage_review")
    void validate_hw2_non_wind_cause_triggers_coverage_review() {
        // Given: Prepare input payload matching entity model
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_form", "HW2");
        payload.put("cause_of_loss", "Fire");
        payload.put("loss_date", "2024-05-15");
        payload.put("policy_number", "POL-HW2-001");
        payload.put("id", "evt-12345");

        // Mock validation outcome
        ValidationResult expectedValidation = new ValidationResult(
            "VALIDATION_ERROR",
            "NON_WIND_PERIL_EXCLUDED",
            "Non-wind peril excluded for HW2",
            true
        );
        when(validationEngine.validate(anyMap())).thenReturn(expectedValidation);

        // Mock external routing & task generation
        doNothing().when(routingService).routeClaim(anyString(), anyString());
        doNothing().when(taskService).generateTask(anyString(), anyString());
        doNothing().when(auditLogger).log(anyString(), anyMap());

        // When: Execute transformation & validation
        TransformationOutcome outcome = handler.processTransformation(payload);

        // Then: Verify expected results
        assertNotNull(outcome, "Outcome should not be null");
        assertTrue(outcome.isHasValidationErrors(), "Should contain validation error");
        assertEquals("NON_WIND_PERIL_EXCLUDED", outcome.getErrorCode(), "Error code should indicate non-wind peril exclusion");
        assertEquals("Non-wind peril excluded for HW2", outcome.getErrorMessage(), "Error message should match policy rule");

        // Verify routing to Coverage Triage
        verify(routingService, times(1)).routeClaim(eq("POL-HW2-001"), eq("Coverage Triage"));

        // Verify Review Coverage task generated
        verify(taskService, times(1)).generateTask(eq("POL-HW2-001"), eq("REVIEW_COVERAGE"));

        // Verify structured logging for observability
        verify(auditLogger, times(1)).log(eq("TRANSFORMATION_VALIDATION"), anyMap());
    }
}
