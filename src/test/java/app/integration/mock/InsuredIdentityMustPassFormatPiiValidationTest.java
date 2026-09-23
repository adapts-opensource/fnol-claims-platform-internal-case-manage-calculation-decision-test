package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for Insured Engagement & Tracking: decision transformation PII validation.
 * Verifies that insured identity data passes format and PII validation rules.
 */
public class InsuredIdentityMustPassFormatPiiValidationTest {

    @Mock
    private PiiValidationService piiValidationService;

    @InjectMocks
    private DecisionTransformationService decisionTransformationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void insured_identity_must_pass_format_pii_validation() {
        // Arrange
        InsuredIdentity insuredIdentity = new InsuredIdentity();
        insuredIdentity.setId("INS-VALID-88429");
        insuredIdentity.setEmail("insured@example.com");
        insuredIdentity.setSsn("123-45-6789");

        // Mock validation service to return true for valid PII
        when(piiValidationService.validatePii(any(InsuredIdentity.class)))
                .thenReturn(true);

        // Act
        DecisionTransformationResult result = decisionTransformationService.transform(insuredIdentity);

        // Assert
        assertNotNull(result, "Transformation result should not be null");
        assertTrue(result.isPiiValid(), "Insured identity must pass format/PII validation");
        verify(piiValidationService, times(1)).validatePii(insuredIdentity);
    }
}
