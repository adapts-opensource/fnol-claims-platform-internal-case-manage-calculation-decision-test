package app.integration.mock;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("input_criteria_required_inputs_rule_id_new_values_effective_date_optional_inputs_approval_required_rollback_version_input_validation_new_values_must_match_schema_effective_date_must_be_current_date_approval_required_must_be_boolean_freshness_requirements_configuration_must_be_validated_against_latest_schema_version_effective_date_must_not_conflict_with_active_version")
public class InputCriteriaRequiredInputsRuleIdNewValuesEffectiveDateOptionalInputsApprovalRequiredTest {

    @Mock
    private InputCriteriaValidator inputCriteriaValidator;
    @Mock
    private SchemaValidator schemaValidator;
    @Mock
    private DateValidator dateValidator;
    @Mock
    private TypeValidator typeValidator;
    @Mock
    private VersionConflictChecker versionConflictChecker;

    private StateTransitionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new StateTransitionOrchestrator(
                inputCriteriaValidator,
                schemaValidator,
                dateValidator,
                typeValidator,
                versionConflictChecker
        );
    }

    @Nested
    @DisplayName("Validation Scenarios")
    class ValidationScenarios {

        @Test
        @DisplayName("Valid required and optional inputs should pass orchestration")
        void validInputs_shouldPassOrchestration() {
            Map<String, Object> input = Map.of(
                    "rule_id", "RULE_001",
                    "new_values", Map.of("claimStatus", "APPROVED"),
                    "effective_date", LocalDate.now().toString(),
                    "approval_required", true,
                    "rollback_version", "v2.1"
            );

            when(inputCriteriaValidator.validateRequired(input)).thenReturn(true);
            when(schemaValidator.validate(input.get("new_values"))).thenReturn(true);
            when(dateValidator.isCurrentOrFuture(input.get("effective_date"))).thenReturn(true);
            when(typeValidator.isBoolean(input.get("approval_required"))).thenReturn(true);
            when(schemaValidator.validateLatestSchemaVersion(input)).thenReturn(true);
            when(versionConflictChecker.checkConflict(input.get("effective_date"))).thenReturn(false);

            assertDoesNotThrow(() -> orchestrator.processTransition(input));
            verify(inputCriteriaValidator).validateRequired(input);
            verify(schemaValidator).validate(input.get("new_values"));
            verify(dateValidator).isCurrentOrFuture(input.get("effective_date"));
            verify(typeValidator).isBoolean(input.get("approval_required"));
            verify(schemaValidator).validateLatestSchemaVersion(input);
            verify(versionConflictChecker).checkConflict(input.get("effective_date"));
        }

        @Test
        @DisplayName("Missing required inputs should throw IllegalArgumentException")
        void missingRequiredInputs_shouldThrowException() {
            Map<String, Object> input = Map.of("rule_id", "RULE_001");

            when(inputCriteriaValidator.validateRequired(input)).thenThrow(
                    new IllegalArgumentException("Missing required inputs: new_values, effective_date")
            );

            assertThrows(IllegalArgumentException.class, () -> orchestrator.processTransition(input));
        }

        @Test
        @DisplayName("new_values must match schema")
        void newValuesSchemaMismatch_shouldThrowException() {
            Map<String, Object> input = Map.of(
                    "rule_id", "RULE_001",
                    "new_values", Map.of("invalidField", 123),
                    "effective_date", LocalDate.now().toString()
            );

            when(inputCriteriaValidator.validateRequired(input)).thenReturn(true);
            when(schemaValidator.validate(input.get("new_values"))).thenThrow(
                    new IllegalArgumentException("new_values must match schema")
            );

            assertThrows(IllegalArgumentException.class, () -> orchestrator.processTransition(input));
        }

        @Test
        @DisplayName("effective_date must be >= current date")
        void effectiveDateInPast_shouldThrowException() {
            Map<String, Object> input = Map.of(
                    "rule_id", "RULE_001",
                    "new_values", Map.of("status", "PENDING"),
                    "effective_date", LocalDate.now().minusDays(1).toString()
            );

            when(inputCriteriaValidator.validateRequired(input)).thenReturn(true);
            when(schemaValidator.validate(input.get("new_values"))).thenReturn(true);
            when(dateValidator.isCurrentOrFuture(input.get("effective_date"))).thenThrow(
                    new IllegalArgumentException("effective_date must be >= current date")
            );

            assertThrows(IllegalArgumentException.class, () -> orchestrator.processTransition(input));
        }

        @Test
        @DisplayName("approval_required must be boolean")
        void approvalRequiredNotBoolean_shouldThrowException() {
            Map<String, Object> input = Map.of(
                    "rule_id", "RULE_001",
                    "new_values", Map.of("status", "PENDING"),
                    "effective_date", LocalDate.now().toString(),
                    "approval_required", "true"
            );

            when(inputCriteriaValidator.validateRequired(input)).thenReturn(true);
            when(schemaValidator.validate(input.get("new_values"))).thenReturn(true);
            when(dateValidator.isCurrentOrFuture(input.get("effective_date"))).thenReturn(true);
            when(typeValidator.isBoolean(input.get("approval_required"))).thenThrow(
                    new IllegalArgumentException("approval_required must be boolean")
            );

            assertThrows(IllegalArgumentException.class, () -> orchestrator.processTransition(input));
        }

        @Test
        @DisplayName("Configuration must be validated against latest schema version")
        void configSchemaVersionMismatch_shouldThrowException() {
            Map<String, Object> input = Map.of(
                    "rule_id", "RULE_001",
                    "new_values", Map.of("status", "PENDING"),
                    "effective_date", LocalDate.now().toString()
            );

            when(inputCriteriaValidator.validateRequired(input)).thenReturn(true);
            when(schemaValidator.validate(input.get("new_values"))).thenReturn(true);
            when(dateValidator.isCurrentOrFuture(input.get("effective_date"))).thenReturn(true);
            when(schemaValidator.validateLatestSchemaVersion(input)).thenThrow(
                    new IllegalArgumentException("Configuration must be validated against latest schema version")
            );

            assertThrows(IllegalArgumentException.class, () -> orchestrator.processTransition(input));
        }

        @Test
        @DisplayName("Effective date must not conflict with active version")
        void effectiveDateConflictsWithActiveVersion_shouldThrowException() {
            Map<String, Object> input = Map.of(
                    "rule_id", "RULE_001",
                    "new_values", Map.of("status", "PENDING"),
                    "effective_date", LocalDate.now().toString()
            );

            when(inputCriteriaValidator.validateRequired(input)).thenReturn(true);
            when(schemaValidator.validate(input.get("new_values"))).thenReturn(true);
            when(dateValidator.isCurrentOrFuture(input.get("effective_date"))).thenReturn(true);
            when(schemaValidator.validateLatestSchemaVersion(input)).thenReturn(true);
            when(versionConflictChecker.checkConflict(input.get("effective_date"))).thenThrow(
                    new IllegalArgumentException("Effective date must not conflict with active version")
            );

            assertThrows(IllegalArgumentException.class, () -> orchestrator.processTransition(input));
        }
    }

    // Mock interfaces representing external I/O and validation contracts
    interface InputCriteriaValidator {
        boolean validateRequired(Map<String, Object> input);
    }

    interface SchemaValidator {
        boolean validate(Object newValues);
        boolean validateLatestSchemaVersion(Map<String, Object> input);
    }

    interface DateValidator {
        boolean isCurrentOrFuture(Object effectiveDate);
    }

    interface TypeValidator {
        boolean isBoolean(Object value);
    }

    interface VersionConflictChecker {
        boolean checkConflict(Object effectiveDate);
    }

    // Production-like orchestrator under test
    static class StateTransitionOrchestrator {
        private final InputCriteriaValidator inputCriteriaValidator;
        private final SchemaValidator schemaValidator;
        private final DateValidator dateValidator;
        private final TypeValidator typeValidator;
        private final VersionConflictChecker versionConflictChecker;

        StateTransitionOrchestrator(InputCriteriaValidator inputCriteriaValidator,
                                    SchemaValidator schemaValidator,
                                    DateValidator dateValidator,
                                    TypeValidator typeValidator,
                                    VersionConflictChecker versionConflictChecker) {
            this.inputCriteriaValidator = inputCriteriaValidator;
            this.schemaValidator = schemaValidator;
            this.dateValidator = dateValidator;
            this.typeValidator = typeValidator;
            this.versionConflictChecker = versionConflictChecker;
        }

        void processTransition(Map<String, Object> input) {
            inputCriteriaValidator.validateRequired(input);
            schemaValidator.validate(input.get("new_values"));
            dateValidator.isCurrentOrFuture(input.get("effective_date"));
            if (input.containsKey("approval_required")) {
                typeValidator.isBoolean(input.get("approval_required"));
            }
            schemaValidator.validateLatestSchemaVersion(input);
            versionConflictChecker.checkConflict(input.get("effective_date"));
        }
    }
}
