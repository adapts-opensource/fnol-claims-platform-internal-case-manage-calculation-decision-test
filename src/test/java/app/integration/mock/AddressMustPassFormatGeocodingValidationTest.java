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
public class AddressMustPassFormatGeocodingValidationTest {

    @Mock
    private AddressFormatValidationService addressFormatValidationService;

    @Mock
    private GeocodingValidationService geocodingValidationService;

    private ClaimDataStandardizationDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new ClaimDataStandardizationDecisionService(addressFormatValidationService, geocodingValidationService);
    }

    @Test
    void address_must_pass_format_geocoding_validation() {
        // Arrange
        String claimId = "CLM-12345";
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "claimant", Map.of(
                "address", Map.of(
                    "street", "123 Main St",
                    "city", "Springfield",
                    "state", "IL",
                    "zipCode", "62704"
                )
            )
        );

        when(addressFormatValidationService.validate(anyString())).thenReturn(true);
        when(geocodingValidationService.geocode(anyString())).thenReturn(Map.of("lat", 39.7817, "lon", -89.6501));

        // Act
        Map<String, Object> decision = decisionService.evaluate(payload);

        // Assert
        assertNotNull(decision);
        assertEquals("VALID", decision.get("validationStatus"));
        assertTrue((boolean) decision.get("addressValid"));
        verify(addressFormatValidationService, times(1)).validate(anyString());
        verify(geocodingValidationService, times(1)).geocode(anyString());
    }
}
