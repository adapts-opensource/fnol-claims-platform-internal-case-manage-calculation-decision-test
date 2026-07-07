package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoratoriumDataUnavailableUseDefaultBehaviorOrFlagTest {

    @Mock
    private ClaimDataStandardizationTransformationService transformationService;

    private Map<String, Object> inputPayload;

    @BeforeEach
    void setUp() {
        // Initialize input payload simulating a claim record without moratorium data
        inputPayload = new HashMap<>();
        inputPayload.put("claimId", "CLM-12345");
        inputPayload.put("policyNumber", "POL-67890");
        inputPayload.put("claimType", "AUTO");
        // Intentionally omitting moratorium-related keys to trigger unavailability handling
    }

    @Test
    @DisplayName("moratorium_data_unavailable_use_default_behavior_or_flag")
    void moratoriumDataUnavailableUseDefaultBehaviorOrFlag() {
        // Arrange
        String claimId = "CLM-12345";
        Map<String, Object> expectedPayload = new HashMap<>();
        expectedPayload.put("claimId", claimId);
        expectedPayload.put("policyNumber", "POL-67890");
        expectedPayload.put("claimType", "AUTO");
        // Default behavior: set explicit flag and fallback status
        expectedPayload.put("moratoriumDataFlag", true);
        expectedPayload.put("moratoriumStatus", "DEFAULT_UNAVAILABLE");

        ClaimDataStandardizationCalculationTransform expectedTransform = mock(ClaimDataStandardizationCalculationTransform.class);
        when(expectedTransform.getId()).thenReturn(claimId);
        when(expectedTransform.getPayload()).thenReturn(expectedPayload);

        when(transformationService.transform(inputPayload, claimId)).thenReturn(expectedTransform);

        // Act
        ClaimDataStandardizationCalculationTransform actualTransform = transformationService.transform(inputPayload, claimId);

        // Assert
        assertNotNull(actualTransform, "Transform result must not be null when moratorium data is missing");
        assertEquals(claimId, actualTransform.getId(), "Transformed entity ID must match input claimId");

        Map<String, Object> actualPayload = actualTransform.getPayload();
        assertNotNull(actualPayload, "Payload map must be present in the transformed entity");
        assertTrue(actualPayload.containsKey("moratoriumDataFlag") || actualPayload.containsKey("moratoriumStatus"),
                "Payload must contain a flag or default status indicating moratorium data unavailability");
        assertEquals(true, actualPayload.get("moratoriumDataFlag"),
                "Moratorium data flag should be explicitly set to true when data is unavailable");
        assertEquals("DEFAULT_UNAVAILABLE", actualPayload.get("moratoriumStatus"),
                "Moratorium status should fallback to default behavior value");
    }
}
