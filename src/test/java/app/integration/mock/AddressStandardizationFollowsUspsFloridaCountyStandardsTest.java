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
class AddressStandardizationUspsFloridaCountyStandardsTest {

    @Mock
    private AddressValidationService addressValidationService;

    private ValidationDecisionEngine validationDecisionEngine;

    @BeforeEach
    void setUp() {
        validationDecisionEngine = new ValidationDecisionEngine(addressValidationService);
    }

    @Test
    void address_standardization_follows_usps_florida_county_standards() {
        // Given: Raw Florida address input requiring standardization
        String rawAddress = "1234 NW 123RD ST APT 4B, MIAMI, FL 33126";
        Map<String, String> expectedStandardized = Map.of(
                "street_line", "1234 NW 123RD ST APT 4B",
                "city", "MIAMI",
                "state", "FL",
                "zip5", "33126",
                "county", "MIAMI-DADE",
                "usps_compliant", "true"
        );

        // Mock external address validation service response (simulates USPS/county lookup)
        when(addressValidationService.standardize(rawAddress)).thenReturn(expectedStandardized);

        // When: Validation decision engine processes the FNOL submission address
        Map<String, String> decision = validationDecisionEngine.evaluate(rawAddress);

        // Then: Verify decision follows USPS/Florida county standards
        assertNotNull(decision, "Validation decision must not be null");
        assertEquals(expectedStandardized.get("street_line"), decision.get("street_line"), "Street line must be USPS standardized");
        assertEquals(expectedStandardized.get("city"), decision.get("city"), "City must match input");
        assertEquals(expectedStandardized.get("state"), decision.get("state"), "State must be FL");
        assertEquals(expectedStandardized.get("zip5"), decision.get("zip5"), "ZIP5 must be standardized");
        assertEquals(expectedStandardized.get("county"), decision.get("county"), "County must be MIAMI-DADE per Florida standards");
        assertEquals("true", decision.get("usps_compliant"), "Address must be marked USPS compliant");

        verify(addressValidationService, times(1)).standardize(rawAddress);
    }
}
