package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 test class for Claim Initiation & Routing:decision:validation feature.
 * Verifies claim number generation, format compliance, and uniqueness constraints.
 */
@ExtendWith(MockitoExtension.class)
class ClaimNumberGenTest {

    @Mock
    private ClaimNumberGeneratorService claimNumberGeneratorService;

    @Mock
    private UniquenessValidatorService uniquenessValidatorService;

    @Mock
    private PatternValidationService patternValidationService;

    @InjectMocks
    private ClaimInitiationRoutingDecisionValidationService service;

    private static final String TENANT_CODE = "FL01";
    private static final int YEAR = 2024;
    private static final int CURRENT_MAX_SEQ = 1234;
    private static final String EXPECTED_CLAIM_NUMBER = "CLM-FL01-2024-00001235";
    private static final String REGEX_PATTERN = "CLM-[A-Z0-9]{2,4}-\\d{4}-\\d{7,8}";

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection automatically
    }

    @Test
    void validate_claim_number_format_uniqueness() {
        // Arrange: Mock external dependencies
        when(claimNumberGeneratorService.generate(TENANT_CODE, YEAR, CURRENT_MAX_SEQ))
                .thenReturn(EXPECTED_CLAIM_NUMBER);

        when(uniquenessValidatorService.isUnique(EXPECTED_CLAIM_NUMBER, TENANT_CODE, YEAR))
                .thenReturn(true);

        when(patternValidationService.isValid(EXPECTED_CLAIM_NUMBER, REGEX_PATTERN))
                .thenReturn(true);

        // Act: Execute service method
        String result = service.validateAndGenerateClaimNumber(TENANT_CODE, YEAR, CURRENT_MAX_SEQ);

        // Assert: Verify results and interactions
        assertNotNull(result, "Generated claim number should not be null");
        assertEquals(EXPECTED_CLAIM_NUMBER, result, "Claim number should match expected format and sequence");

        // Verify mock interactions
        verify(claimNumberGeneratorService, times(1))
                .generate(TENANT_CODE, YEAR, CURRENT_MAX_SEQ);

        verify(uniquenessValidatorService, times(1))
                .isUnique(EXPECTED_CLAIM_NUMBER, TENANT_CODE, YEAR);

        verify(patternValidationService, times(1))
                .isValid(EXPECTED_CLAIM_NUMBER, REGEX_PATTERN);
    }
}
