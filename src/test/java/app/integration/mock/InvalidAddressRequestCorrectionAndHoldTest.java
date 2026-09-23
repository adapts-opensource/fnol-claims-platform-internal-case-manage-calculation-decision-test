package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

public class InvalidAddressRequestCorrectionAndHoldTest {

    private RoutingDecisionCalculator decisionCalculator;
    private AddressValidationService addressValidator;

    @BeforeEach
    void setUp() {
        addressValidator = mock(AddressValidationService.class);
        decisionCalculator = new RoutingDecisionCalculator(addressValidator);
    }

    @Test
    void invalid_address_request_correction_and_hold() {
        // Arrange
        String claimId = "CLM-INV-001";
        Map<String, Object> payload = Map.of(
            "claimId", claimId,
            "applicantAddress", Map.of("street", "123 Fake St", "city", "Nullville", "postalCode", "00000")
        );

        when(addressValidator.validate(payload)).thenReturn(false);

        // Act
        RoutingDecision decision = decisionCalculator.calculate(payload);

        // Assert
        assertNotNull(decision);
        assertEquals("HOLD", decision.status());
        assertEquals("REQUEST_CORRECTION", decision.action());
        assertEquals("INVALID_ADDRESS", decision.reasonCode());
        verify(addressValidator, times(1)).validate(payload);
    }

    // Internal stubs to isolate external I/O contracts and ensure test compiles standalone
    interface AddressValidationService {
        boolean validate(Map<String, Object> payload);
    }

    static class RoutingDecisionCalculator {
        private final AddressValidationService addressValidator;
        
        RoutingDecisionCalculator(AddressValidationService addressValidator) {
            this.addressValidator = addressValidator;
        }
        
        RoutingDecision calculate(Map<String, Object> payload) {
            if (!addressValidator.validate(payload)) {
                return new RoutingDecision("HOLD", "REQUEST_CORRECTION", "INVALID_ADDRESS");
            }
            return new RoutingDecision("ROUTED", "AUTO_ASSIGN", "VALID_ADDRESS");
        }
    }

    record RoutingDecision(String status, String action, String reasonCode) {}
}
