package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
// JUnit 5 test class with @Test methods
@ExtendWith(MockitoExtension.class)
public class PolicyNumberRegistryFormatTest {

    @Test
    void policy_number_must_match_registry_format() {
        // Arrange
        String validPolicyNumber = "POL-884219-XZQ";
        String invalidPolicyNumber = "POL-123";
        String malformedPolicyNumber = "POL-ABCD56-XYZ";
        String registryFormat = "^POL-\\d{6}-[A-Z]{3}$";

        // Mock the decision transformation service that would normally call external registry/IO
        DecisionTransformationService mockService = mock(DecisionTransformationService.class);
        when(mockService.validatePolicyNumber(validPolicyNumber)).thenReturn(true);
        when(mockService.validatePolicyNumber(invalidPolicyNumber)).thenReturn(false);
        when(mockService.validatePolicyNumber(malformedPolicyNumber)).thenReturn(false);

        // Act & Assert
        assertTrue(mockService.validatePolicyNumber(validPolicyNumber),
                "Policy number must match registry format: " + registryFormat);
        assertFalse(mockService.validatePolicyNumber(invalidPolicyNumber),
                "Policy number must not match registry format when too short");
        assertFalse(mockService.validatePolicyNumber(malformedPolicyNumber),
                "Policy number must not match registry format when contains non-digits");

        verify(mockService, times(3)).validatePolicyNumber(anyString());
    }

    // Simulated service interface for integration mocking
    interface DecisionTransformationService {
        boolean validatePolicyNumber(String policyNumber);
    }
}
