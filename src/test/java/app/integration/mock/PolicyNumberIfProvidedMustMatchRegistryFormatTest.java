package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that policy numbers (if provided) match the expected registry format
 * during the insured engagement & tracking decision transformation process.
 */
@ExtendWith(MockitoExtension.class)
public class PolicyNumberTransformationTest {

    @Mock
    private RegistryFormatValidator registryValidator;

    private DecisionTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = new DecisionTransformationService(registryValidator);
    }

    @Test
    void policy_number_if_provided_must_match_registry_format() {
        // Arrange: Mock registry validation for valid and invalid formats
        when(registryValidator.isValidFormat("INS-12345678")).thenReturn(true);
        when(registryValidator.isValidFormat("POL-99887766")).thenReturn(true);
        when(registryValidator.isValidFormat("INVALID-FORMAT")).thenReturn(false);

        // Act & Assert: Valid policy number should pass transformation
        assertDoesNotThrow(() -> transformationService.processPolicy("INS-12345678"));

        // Act & Assert: Invalid policy number must throw validation error
        assertThrows(IllegalArgumentException.class, () -> transformationService.processPolicy("INVALID-FORMAT"));

        // Act & Assert: Null or empty policy numbers are allowed per "if provided" rule
        assertDoesNotThrow(() -> transformationService.processPolicy(null));
        assertDoesNotThrow(() -> transformationService.processPolicy(""));
    }

    // Service under test simulating the transformation step
    static class DecisionTransformationService {
        private final RegistryFormatValidator registryValidator;

        DecisionTransformationService(RegistryFormatValidator registryValidator) {
            this.registryValidator = registryValidator;
        }

        void processPolicy(String policyNumber) {
            if (policyNumber == null || policyNumber.trim().isEmpty()) {
                return; // "if provided" condition satisfied
            }
            if (!registryValidator.isValidFormat(policyNumber)) {
                throw new IllegalArgumentException("Policy number must match registry format");
            }
        }
    }

    // Mocked external registry interface to prevent live AWS/HTTP calls
    interface RegistryFormatValidator {
        boolean isValidFormat(String policyNumber);
    }
}
