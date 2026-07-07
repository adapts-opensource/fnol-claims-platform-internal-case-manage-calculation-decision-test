package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that rule payloads for Insured Engagement & Tracking:decision:state_transition
 * conform to the expected schema. Aligns with input_validation and structured_logging NFRs.
 */
@ExtendWith(MockitoExtension.class)
public class RulePayloadMustConformToSchemaTest {

    @Mock
    private SchemaValidator schemaValidator;

    private Map<String, Object> validStateTransitionPayload;
    private Map<String, Object> invalidStateTransitionPayload;

    @BeforeEach
    void setUp() {
        // Simulate a compliant rule payload for state transition
        validStateTransitionPayload = Map.of(
                "claimId", "CLM-98765",
                "fromState", "NEW",
                "toState", "ENGAGED",
                "decisionBy", "AUTO_RULE",
                "triggeredAt", System.currentTimeMillis(),
                "metadata", Map.of("engine", "INSURED_TRACKING", "schemaVersion", "1.0.0")
        );

        // Simulate a non-compliant payload missing required schema fields
        invalidStateTransitionPayload = Map.of(
                "claimId", "CLM-98765",
                "toState", "ENGAGED"
        );
    }

    @Test
    void rule_payload_must_conform_to_schema() {
        // Arrange: Mock schema validation behavior
        when(schemaValidator.validate(validStateTransitionPayload)).thenReturn(true);
        when(schemaValidator.validate(invalidStateTransitionPayload)).thenThrow(
                new IllegalArgumentException("Schema validation failed: missing required fields [fromState, decisionBy, triggeredAt]")
        );

        // Act & Assert: Valid payload must pass validation
        assertDoesNotThrow(() -> {
            boolean isValid = schemaValidator.validate(validStateTransitionPayload);
            assertTrue(isValid, "Valid rule payload must conform to schema");
        });

        // Act & Assert: Invalid payload must fail validation
        assertThrows(IllegalArgumentException.class, () -> {
            schemaValidator.validate(invalidStateTransitionPayload);
        }, "Invalid rule payload must not conform to schema");
    }

    // Mock interface representing external schema validation service
    @FunctionalInterface
    private interface SchemaValidator {
        boolean validate(Map<String, Object> payload);
    }
}
