package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AddressMustPassFormatAndGeocodingValidationTest {

    @Mock
    private AddressFormatValidator addressFormatValidator;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private RedisCache redisCache;

    @Mock
    private DynamoDbStore dynamoDbStore;

    private ClaimRoutingDecisionService claimRoutingDecisionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        claimRoutingDecisionService = new ClaimRoutingDecisionService(addressFormatValidator, geocodingService, redisCache, dynamoDbStore);
    }

    @Test
    void address_must_pass_format_and_geocoding_validation() {
        // Given
        String claimId = "claim-init-001";
        Map<String, Object> payload = Map.of(
            "streetAddress", "123 Main St",
            "city", "Springfield",
            "state", "IL",
            "zipCode", "62704",
            "country", "US"
        );

        when(addressFormatValidator.validate(payload)).thenReturn(true);
        when(geocodingService.geocode(payload)).thenReturn(new GeocodingResult(39.7817, -89.6501, "VALID"));

        // When
        Map<String, Object> validationResult = claimRoutingDecisionService.calculateRoutingDecision(claimId, payload);

        // Then
        assertNotNull(validationResult, "Validation result payload should not be null");
        assertEquals("VALID", validationResult.get("geocodingStatus"), "Geocoding status should be VALID");
        assertTrue((Boolean) validationResult.get("formatValid"), "Address format should be valid");
        assertEquals(claimId, validationResult.get("claimId"), "Claim ID should match");

        verify(addressFormatValidator).validate(payload);
        verify(geocodingService).geocode(payload);
        verify(redisCache).put(eq("Cache & Reference Data:cache:" + claimId), anyString(), eq(3600));
        verify(dynamoDbStore).putItem(eq("Claims & Policy Data Store_table"), anyMap());
    }

    interface AddressFormatValidator { boolean validate(Map<String, Object> payload); }
    interface GeocodingService { GeocodingResult geocode(Map<String, Object> payload); }
    static class GeocodingResult {
        private final double latitude;
        private final double longitude;
        private final String status;
        GeocodingResult(double latitude, double longitude, String status) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.status = status;
        }
        double getLatitude() { return latitude; }
        double getLongitude() { return longitude; }
        String getStatus() { return status; }
    }
    interface RedisCache { String get(String key); void put(String key, String value, int ttl); }
    interface DynamoDbStore { Map<String, Object> getItem(String tableName, String pk); void putItem(String tableName, Map<String, Object> item); }

    static class ClaimRoutingDecisionService {
        private final AddressFormatValidator addressFormatValidator;
        private final GeocodingService geocodingService;
        private final RedisCache redisCache;
        private final DynamoDbStore dynamoDbStore;

        ClaimRoutingDecisionService(AddressFormatValidator afv, GeocodingService gs, RedisCache rc, DynamoDbStore dds) {
            this.addressFormatValidator = afv;
            this.geocodingService = gs;
            this.redisCache = rc;
            this.dynamoDbStore = dds;
        }

        Map<String, Object> calculateRoutingDecision(String claimId, Map<String, Object> payload) {
            boolean formatValid = addressFormatValidator.validate(payload);
            GeocodingResult geo = geocodingService.geocode(payload);

            Map<String, Object> result = Map.of(
                "claimId", claimId,
                "formatValid", formatValid,
                "geocodingStatus", geo.getStatus(),
                "latitude", geo.getLatitude(),
                "longitude", geo.getLongitude()
            );

            redisCache.put("Cache & Reference Data:cache:" + claimId, "cached_value", 3600);
            dynamoDbStore.putItem("Claims & Policy Data Store_table", result);

            return result;
        }
    }
}
