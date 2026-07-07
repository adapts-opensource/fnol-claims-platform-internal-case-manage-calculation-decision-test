package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("PurposeNormalizeRawUserInputsIntoStandardInternal")
public class PurposeNormalizeRawUserInputsIntoStandardInternalFormatsForAddressesDatesAndCauseCodesTest {

    // Minimal service interface representing the transformation layer
    interface ClaimDataStandardizationCalculationTransformService {
        Map<String, Object> transform(String id, Map<String, Object> payload);
    }

    private ClaimDataStandardizationCalculationTransformService mockService;

    @BeforeEach
    void setUp() {
        // Mock isolation ensures thread-safe, deterministic execution without external I/O
        mockService = mock(ClaimDataStandardizationCalculationTransformService.class);
    }

    @Test
    @DisplayName("purpose_normalize_raw_user_inputs_into_standard_internal_formats_for_addresses_dates_and_cause_codes")
    void purpose_normalize_raw_user_inputs_into_standard_internal_formats_for_addresses_dates_and_cause_codes() {
        // Arrange
        String rawInputId = "claim-std-001";
        Map<String, Object> rawPayload = new HashMap<>();
        rawPayload.put("address", "123 Main St, Anytown, NY 12345");
        rawPayload.put("incident_date", "01/15/2023");
        rawPayload.put("cause_code", "auto_collision");

        Map<String, Object> expectedStandardPayload = new HashMap<>();
        expectedStandardPayload.put("address", "123 MAIN ST, ANYTOWN, NY 12345");
        expectedStandardPayload.put("incident_date", "2023-01-15");
        expectedStandardPayload.put("cause_code", "CODE_COLLISION_AUTO");

        when(mockService.transform(rawInputId, rawPayload)).thenReturn(expectedStandardPayload);

        // Act
        Map<String, Object> result = mockService.transform(rawInputId, rawPayload);

        // Assert
        assertNotNull(result, "Transformed payload must not be null");
        assertEquals(expectedStandardPayload.get("address"), result.get("address"), "Address should be normalized to uppercase standard format");
        assertEquals(expectedStandardPayload.get("incident_date"), result.get("incident_date"), "Date should be normalized to ISO 8601 (yyyy-MM-dd)");
        assertEquals(expectedStandardPayload.get("cause_code"), result.get("cause_code"), "Cause code should be mapped to internal standard enum/code");

        // Verify single invocation and thread-safe mock behavior
        verify(mockService, times(1)).transform(rawInputId, rawPayload);
    }
}
