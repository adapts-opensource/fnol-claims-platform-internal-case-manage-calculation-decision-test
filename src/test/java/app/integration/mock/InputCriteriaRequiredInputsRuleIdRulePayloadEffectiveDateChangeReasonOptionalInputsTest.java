package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;

/**
 * JUnit 5 mock test for Multi-Channel FNOL Submission:orchestration:validation.
 * Verifies input criteria validation, required/optional handling, and schema/date constraints.
 * Mocks external I/O to avoid live dependencies.
 */
@ExtendWith(MockitoExtension.class)
class InputCriteriaRequiredInputsRuleIdRulePayloadEffectiveDateChangeReasonOptionalInputsTest {

    @Mock
    private ValidationContract validationContract;

    @InjectMocks
    private FnolOrchestrationService fnolOrchestrationService;

    @Test
    @DisplayName("Should pass validation when all required inputs are present and valid")
    void testPass_AllRequiredPresent_OptionalAbsent() {
        // Arrange
        Map<String, Object> inputs = Map.of(
            "rule_id", "RULE_FNOL_001",
            "rule_payload", Map.of("claimType", "AUTO", "details", "Collision"),
            "effective_date", "2023-10-27",
            "change_reason", "Initial Submission"
        );

        // Mock external I/O: Schema validation passes
        org.mockito.Mockito.when(validationContract.validateSchema(org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(true);
        // Mock external I/O: Date validation passes
        org.mockito.Mockito.when(validationContract.validateDate(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(true);

        // Act & Assert
        Assertions.assertDoesNotThrow(() -> fnolOrchestrationService.processSubmission(inputs),
            "Validation should pass with all required inputs present and valid.");
    }

    @Test
    @DisplayName("Should fail validation when required input rule_id is missing")
    void testFail_MissingRequiredInputRuleId() {
        // Arrange
        Map<String, Object> inputs = Map.of(
            "rule_payload", Map.of("claimType", "AUTO"),
            "effective_date", "2023-10-27",
            "change_reason", "Initial Submission"
        );

        // Act & Assert
        Assertions.assertThrows(IllegalArgumentException.class, () -> fnolOrchestrationService.processSubmission(inputs),
            "Validation should fail when rule_id is missing.");
    }

    @Test
    @DisplayName("Should fail validation when required input rule_payload is missing")
    void testFail_MissingRequiredInputRulePayload() {
        // Arrange
        Map<String, Object> inputs = Map.of(
            "rule_id", "RULE_FNOL_001",
            "effective_date", "2023-10-27",
            "change_reason", "Initial Submission"
        );

        // Act & Assert
        Assertions.assertThrows(IllegalArgumentException.class, () -> fnolOrchestrationService.processSubmission(inputs),
            "Validation should fail when rule_payload is missing.");
    }

    @Test
    @DisplayName("Should fail validation when required input effective_date is missing")
    void testFail_MissingRequiredInputEffectiveDate() {
        // Arrange
        Map<String, Object> inputs = Map.of(
            "rule_id", "RULE_FNOL_001",
            "rule_payload", Map.of("claimType", "AUTO"),
            "change_reason", "Initial Submission"
        );

        // Act & Assert
        Assertions.assertThrows(IllegalArgumentException.class, () -> fnolOrchestrationService.processSubmission(inputs),
            "Validation should fail when effective_date is missing.");
    }

    @Test
    @DisplayName("Should fail validation when required input change_reason is missing")
    void testFail_MissingRequiredInputChangeReason() {
        // Arrange
        Map<String, Object> inputs = Map.of(
            "rule_id", "RULE_FNOL_001",
            "rule_payload", Map.of("claimType", "AUTO"),
            "effective_date", "2023-10-27"
        );

        // Act & Assert
        Assertions.assertThrows(IllegalArgumentException.class, () -> fnolOrchestrationService.processSubmission(inputs),
            "Validation should fail when change_reason is missing.");
    }

    @Test
    @DisplayName("Should fail validation when rule_payload fails schema validation")
    void testFail_RulePayloadFailsSchemaValidation() {
        // Arrange
        Map<String, Object> inputs = Map.of(
            "rule_id", "RULE_FNOL_001",
            "rule_payload", Map.of("invalidKey", "value"),
            "effective_date", "2023-10-27",
            "change_reason", "Initial Submission"
        );

        // Mock external I/O: Schema validation fails
        org.mockito.Mockito.when(validationContract.validateSchema(org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(false);

        // Act & Assert
        Assertions.assertThrows(RuntimeException.class, () -> fnolOrchestrationService.processSubmission(inputs),
            "Validation should fail when rule_payload does not pass schema validation.");
    }

    @Test
    @DisplayName("Should fail validation when effective_date is invalid")
    void testFail_EffectiveDateInvalid() {
        // Arrange
        Map<String, Object> inputs = Map.of(
            "rule_id", "RULE_FNOL_001",
            "rule_payload", Map.of("claimType", "AUTO"),
            "effective_date", "INVALID-DATE-FORMAT",
            "change_reason", "Initial Submission"
        );

        // Mock external I/O: Date validation fails
        org.mockito.Mockito.when(validationContract.validateDate(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(false);

        // Act & Assert
        Assertions.assertThrows(RuntimeException.class, () -> fnolOrchestrationService.processSubmission(inputs),
            "Validation should fail when effective_date is invalid.");
    }

    @Test
    @DisplayName("Should pass validation when optional inputs are present and valid")
    void testPass_OptionalInputsPresentAndValid() {
        // Arrange
        Map<String, Object> inputs = Map.of(
            "rule_id", "RULE_FNOL_001",
            "rule_payload", Map.of("claimType", "AUTO", "details", "Collision"),
            "effective_date", "2023-10-27",
            "change_reason", "Initial Submission",
            "test_scenarios", List.of("scenario_1"),
            "rollback_trigger_condition", Map.of("threshold", 100),
            "stakeholder_approval_refs", List.of("USR_001", "USR_002")
        );

        // Mock external I/O: Validations pass
        org.mockito.Mockito.when(validationContract.validateSchema(org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(true);
        org.mockito.Mockito.when(validationContract.validateDate(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(true);

        // Act & Assert
        Assertions.assertDoesNotThrow(() -> fnolOrchestrationService.processSubmission(inputs),
            "Validation should pass when optional inputs are present and valid.");
    }

    @Test
    @DisplayName("Should pass validation when optional inputs are absent")
    void testPass_OptionalInputsAbsent() {
        // Arrange
        Map<String, Object> inputs = Map.of(
            "rule_id", "RULE_FNOL_001",
            "rule_payload", Map.of("claimType", "AUTO", "details", "Collision"),
            "effective_date", "2023-10-27",
            "change_reason", "Initial Submission"
        );

        // Mock external I/O: Validations pass
        org.mockito.Mockito.when(validationContract.validateSchema(org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(true);
        org.mockito.Mockito.when(validationContract.validateDate(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(true);

        // Act & Assert
        Assertions.assertDoesNotThrow(() -> fnolOrchestrationService.processSubmission(inputs),
            "Validation should pass when optional inputs are absent.");
    }
}
