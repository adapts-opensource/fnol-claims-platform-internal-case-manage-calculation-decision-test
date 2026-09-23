package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class DamageAreaOrchestrationTest {

    @Mock
    private ClaimDataTransformationService transformationService;

    @InjectMocks
    private ClaimDataOrchestrationService orchestrationService;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testPayload = new HashMap<>();
        testPayload.put("id", "claim-orch-001");
        testPayload.put("damage_area", "VEHICLE_REAR");
    }

    @Test
    void damage_area() {
        // Arrange: Define expected standardized output
        Map<String, Object> expectedPayload = new HashMap<>();
        expectedPayload.put("id", "claim-orch-001");
        expectedPayload.put("damage_area", "REAR");

        when(transformationService.standardizePayload(testPayload)).thenReturn(expectedPayload);

        // Act: Invoke orchestration pipeline
        Map<String, Object> result = orchestrationService.transform(testPayload);

        // Assert: Verify transformation results and mock interaction
        assertNotNull(result, "Transformed payload must not be null");
        assertEquals("REAR", result.get("damage_area"), "Damage area must be standardized to canonical value");
        assertEquals("claim-orch-001", result.get("id"), "Entity ID must remain unchanged");
        verify(transformationService, times(1)).standardizePayload(testPayload);
    }
}
