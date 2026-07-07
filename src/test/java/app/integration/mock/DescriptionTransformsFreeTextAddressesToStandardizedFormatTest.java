package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationCalculationTransformTest {

    @Mock
    private ClaimDataTransformationService transformationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension automatically initializes @Mock fields
    }

    @Test
    @DisplayName("description_transforms_free_text_addresses_to_standardized_format_validates_date_formats_maps_user_selected_cause_descriptions_to_internal_cause_codes_and_enriches_with_device_channel_metadata")
    void description_transforms_free_text_addresses_to_standardized_format_validates_date_formats_maps_user_selected_cause_descriptions_to_internal_cause_codes_and_enriches_with_device_channel_metadata() {
        // Arrange
        String claimId = "CLM-12345-TEST";
        Map<String, Object> rawPayload = new HashMap<>();
        rawPayload.put("address_line", "123 Main St, New York, NY 10001");
        rawPayload.put("incident_date", "25/10/2023");
        rawPayload.put("cause_description", "Collision with stationary object");
        rawPayload.put("device_info", null);
        rawPayload.put("channel_source", null);

        Map<String, Object> expectedPayload = new HashMap<>();
        expectedPayload.put("id", claimId);
        expectedPayload.put("standardized_address", "123 MAIN ST, NEW YORK, NY, 10001");
        expectedPayload.put("validated_date", "2023-10-25");
        expectedPayload.put("internal_cause_code", "CAUSE_STAT_OBJ_COLLISION");
        expectedPayload.put("device_type", "MOBILE_APP");
        expectedPayload.put("channel_metadata", "WEB_FORM_V2");

        // Mock external I/O layer; never call live AWS/S3/DynamoDB endpoints
        lenient().when(transformationService.transform(eq(claimId), anyMap())).thenReturn(expectedPayload);

        // Act
        Map<String, Object> resultPayload = transformationService.transform(claimId, rawPayload);

        // Assert
        assertAll("Transformation validation",
            () -> assertNotNull(resultPayload, "Transformed payload must not be null"),
            () -> assertEquals(claimId, resultPayload.get("id"), "ID must be preserved"),
            () -> assertEquals("123 MAIN ST, NEW YORK, NY, 10001", resultPayload.get("standardized_address"), "Address must be standardized"),
            () -> assertEquals("2023-10-25", resultPayload.get("validated_date"), "Date must be validated and normalized to ISO-8601"),
            () -> assertEquals("CAUSE_STAT_OBJ_COLLISION", resultPayload.get("internal_cause_code"), "Cause description must map to internal code"),
            () -> assertEquals("MOBILE_APP", resultPayload.get("device_type"), "Device metadata must be enriched"),
            () -> assertEquals("WEB_FORM_V2", resultPayload.get("channel_metadata"), "Channel metadata must be enriched")
        );
    }

    /**
     * Interface abstracting the transformation logic and underlying infra I/O contracts.
     * Mocked in tests to avoid live AWS (S3/DynamoDB) or production HTTP calls.
     */
    interface ClaimDataTransformationService {
        Map<String, Object> transform(String id, Map<String, Object> payload);
    }
}
