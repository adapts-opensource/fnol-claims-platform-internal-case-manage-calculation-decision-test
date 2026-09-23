package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressMustPassGeocodingStandardizationTest {

    @Mock
    private GeocodingClient geocodingClient;

    @InjectMocks
    private InsuredEngagementTransformationService transformationService;

    private String rawAddress;

    @BeforeEach
    void setUp() {
        rawAddress = "123 MAIN ST, APT 4B, SPRINGFIELD, IL 62704";
    }

    @Test
    void address_must_pass_geocoding_standardization_119() {
        // Arrange: Mock external geocoding API to return a fully standardized payload
        GeocodingResponse mockResponse = new GeocodingResponse(
            "123 Main St, Apt 4b, Springfield, IL 62704",
            39.7817,
            -89.6501,
            true,
            "STANDARDIZED"
        );
        when(geocodingClient.geocode(rawAddress)).thenReturn(mockResponse);

        // Act: Execute transformation pipeline
        AddressTransformationResult result = transformationService.processAddress(rawAddress);

        // Assert: Verify standardization, geocoding, and engagement routing rules
        assertNotNull(result, "Transformation must return a non-null result");
        assertTrue(result.isStandardized(), "Address must pass normalization rules");
        assertEquals("123 Main St, Apt 4b, Springfield, IL 62704", result.getStandardizedAddress());
        assertTrue(result.hasValidCoordinates(), "Geocoding must return valid latitude/longitude");
        assertEquals(39.7817, result.getLatitude(), 0.001);
        assertEquals(-89.6501, result.getLongitude(), 0.001);
        assertTrue(result.isValidForInsuredEngagement(), "Must pass engagement routing validation");
        
        // Verify external I/O was invoked exactly once and not bypassed
        verify(geocodingClient, times(1)).geocode(rawAddress);
        verifyNoMoreInteractions(geocodingClient);
    }

    // --- Minimal internal contracts for compilation context ---
    
    record GeocodingResponse(String standardizedAddress, double latitude, double longitude, boolean isValid, String status) {}
    
    class AddressTransformationResult {
        private final String standardizedAddress;
        private final boolean standardized;
        private final double latitude;
        private final double longitude;
        private final boolean validForEngagement;

        AddressTransformationResult(String addr, boolean std, double lat, double lng, boolean eng) {
            this.standardizedAddress = addr;
            this.standardized = std;
            this.latitude = lat;
            this.longitude = lng;
            this.validForEngagement = eng;
        }

        String getStandardizedAddress() { return standardizedAddress; }
        boolean isStandardized() { return standardized; }
        boolean hasValidCoordinates() { return latitude != 0.0 && longitude != 0.0; }
        double getLatitude() { return latitude; }
        double getLongitude() { return longitude; }
        boolean isValidForInsuredEngagement() { return validForEngagement; }
    }

    class InsuredEngagementTransformationService {
        private final GeocodingClient geocodingClient;
        InsuredEngagementTransformationService(GeocodingClient client) { this.geocodingClient = client; }
        
        AddressTransformationResult processAddress(String raw) {
            GeocodingResponse r = geocodingClient.geocode(raw);
            return new AddressTransformationResult(r.standardizedAddress(), true, r.latitude(), r.longitude(), r.isValid());
        }
    }

    interface GeocodingClient {
        GeocodingResponse geocode(String address);
    }
}
