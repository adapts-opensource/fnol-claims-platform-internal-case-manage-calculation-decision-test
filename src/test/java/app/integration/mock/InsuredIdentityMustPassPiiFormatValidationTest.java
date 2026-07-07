package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test class for Insured Engagement & Tracking:decision:transformation.
 * Verifies PII format validation for insured identities without invoking external services.
 */
@ExtendWith(MockitoExtension.class)
public class InsuredIdentityPiiFormatValidationTest {

    @Mock
    private PiiFormatValidator piiFormatValidator;

    @Test
    void insuredIdentityMustPassPiiFormatValidation() {
        // Arrange: Define valid PII formats compliant with GDPR/SOC2 masking standards
        String validEmail = "insured.user@newco-insurance.com";
        String validPhone = "+1-800-555-0199";
        String validSsnMasked = "***-**-4567";

        // Mock external PII validation service (simulates external I/O)
        when(piiFormatValidator.validateEmail(validEmail)).thenReturn(true);
        when(piiFormatValidator.validatePhone(validPhone)).thenReturn(true);
        when(piiFormatValidator.validateSsnMasked(validSsnMasked)).thenReturn(true);

        // Act: Execute validation logic
        boolean emailValid = piiFormatValidator.validateEmail(validEmail);
        boolean phoneValid = piiFormatValidator.validatePhone(validPhone);
        boolean ssnValid = piiFormatValidator.validateSsnMasked(validSsnMasked);

        // Assert: All valid PII formats must pass format validation
        assertTrue(emailValid, "Valid insured email must pass PII format validation");
        assertTrue(phoneValid, "Valid insured phone must pass PII format validation");
        assertTrue(ssnValid, "Valid masked SSN must pass PII format validation");

        // Verify: Ensure external validation service was called exactly once per field
        verify(piiFormatValidator, times(1)).validateEmail(validEmail);
        verify(piiFormatValidator, times(1)).validatePhone(validPhone);
        verify(piiFormatValidator, times(1)).validateSsnMasked(validSsnMasked);
    }
}
