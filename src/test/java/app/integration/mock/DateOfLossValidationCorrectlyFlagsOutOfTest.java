package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies Claim Initiation & Routing:decision:calculation logic.
 * NFR Compliance: Thread-safe stateless calculator; structured logging via logger injection (omitted for brevity).
 */
@ExtendWith(MockitoExtension.class)
class DateOfLossValidationCorrectlyFlagsOutOf {

    @Mock
    private ReferenceDataService referenceDataService;

    @Test
    void date_of_loss_validation_correctly_flags_out_of_period_losses() {
        // Arrange
        String policyId = "POL-12345";
        LocalDate outOfPeriodDate = LocalDate.of(2020, 1, 1);
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate expirationDate = LocalDate.of(2023, 12, 31);

        Map<String, Object> payload = Map.of(
            "id", "CLM-98765",
            "policyId", policyId,
            "dateOfLoss", outOfPeriodDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        );

        when(referenceDataService.getPolicyBounds(policyId))
                .thenReturn(new PolicyBounds(effectiveDate, expirationDate));

        ClaimRoutingDecisionCalculator calculator = new ClaimRoutingDecisionCalculator(referenceDataService);

        // Act
        ValidationResult result = calculator.validateClaimInitiation(payload);

        // Assert
        assertNotNull(result, "Validation result should not be null");
        assertFalse(result.isValid(), "Out-of-period loss should be marked invalid");
        assertEquals("DATE_OF_LOSS_OUT_OF_PERIOD", result.getErrorCode(), "Error code must match validation rule");
        verify(referenceDataService, times(1)).getPolicyBounds(policyId);
    }

    // Mocked external I/O contract (Redis/DynamoDB Cache & Reference Data)
    interface ReferenceDataService {
        PolicyBounds getPolicyBounds(String policyId);
    }

    record PolicyBounds(LocalDate effectiveDate, LocalDate expirationDate) {}

    record ValidationResult(boolean valid, String errorCode) {
        public boolean isValid() { return valid; }
        public String getErrorCode() { return errorCode; }
    }

    // Component under test (stateless, thread-safe)
    static class ClaimRoutingDecisionCalculator {
        private final ReferenceDataService referenceDataService;

        ClaimRoutingDecisionCalculator(ReferenceDataService referenceDataService) {
            this.referenceDataService = referenceDataService;
        }

        ValidationResult validateClaimInitiation(Map<String, Object> payload) {
            String policyId = (String) payload.get("policyId");
            String dateOfLossStr = (String) payload.get("dateOfLoss");
            LocalDate dateOfLoss = LocalDate.parse(dateOfLossStr);

            PolicyBounds bounds = referenceDataService.getPolicyBounds(policyId);
            boolean isWithinPeriod = !bounds.effectiveDate.isAfter(dateOfLoss) && !bounds.expirationDate.isBefore(dateOfLoss);

            if (isWithinPeriod) {
                return new ValidationResult(true, null);
            }
            return new ValidationResult(false, "DATE_OF_LOSS_OUT_OF_PERIOD");
        }
    }
}
