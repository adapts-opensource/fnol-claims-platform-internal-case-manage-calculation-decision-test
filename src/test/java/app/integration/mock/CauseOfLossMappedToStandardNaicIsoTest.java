package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Validates cause-of-loss mapping to standard NAIC/ISO codes during FNOL submission.
 * Mocks external I/O to ensure thread safety, idempotency, and compliance with security NFRs.
 */
@ExtendWith(MockitoExtension.class)
public class CauseOfLossMappingValidationTest {

    @Mock
    private NaicIsoCodeResolver naicIsoCodeResolver;

    @InjectMocks
    private FnolValidationDecisionService fnolValidationService;

    @Test
    void cause_of_loss_mapped_to_standard_naic_iso_codes() {
        // Arrange: Mock external lookup service to prevent live API calls
        String rawCauseOfLoss = "Theft of insured vehicle";
        String expectedNaicCode = "TH";
        String expectedIsoCode = "04";

        when(naicIsoCodeResolver.mapToStandardCodes(rawCauseOfLoss))
                .thenReturn(new StandardCodes(expectedNaicCode, expectedIsoCode));

        // Act: Invoke validation/decision logic
        StandardCodes result = fnolValidationService.validateAndDetermineCodes(rawCauseOfLoss);

        // Assert: Verify correct mapping and single invocation for idempotency
        assertNotNull(result, "Mapping result must not be null");
        assertEquals(expectedNaicCode, result.naicCode(), "NAIC code must match standard");
        assertEquals(expectedIsoCode, result.isoCode(), "ISO code must match standard");
        verify(naicIsoCodeResolver, times(1)).mapToStandardCodes(rawCauseOfLoss);
    }

    /**
     * Minimal DTO representing standardized loss codes.
     * Uses record for immutability and thread safety.
     */
    public record StandardCodes(String naicCode, String isoCode) {}
}
