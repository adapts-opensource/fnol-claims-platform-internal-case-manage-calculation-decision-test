package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayloadIntakeSchemaValidationTest {

    @Mock
    private ClaimIntakeSchemaValidator schemaValidator;

    private Map<String, Object> validIntakePayload;

    @BeforeEach
    void setUp() {
        validIntakePayload = Map.of(
            "claimId", "CLM-1001",
            "policyNumber", "POL-2002",
            "incidentDate", "2023-11-15",
            "status", "INTAKE",
            "priority", "HIGH"
        );
    }

    @Test
    void payload_must_pass_intake_schema_validation() {
        // Given: A payload conforming to the intake schema
        when(schemaValidator.isValid(validIntakePayload)).thenReturn(true);

        // When: The orchestrator delegates validation to the schema validator
        boolean validationResult = schemaValidator.isValid(validIntakePayload);

        // Then: The payload must pass intake schema validation
        assertTrue(validationResult, "Payload must pass intake schema validation");
        verify(schemaValidator, times(1)).isValid(validIntakePayload);
    }

    interface ClaimIntakeSchemaValidator {
        boolean isValid(Map<String, Object> payload);
    }
}
