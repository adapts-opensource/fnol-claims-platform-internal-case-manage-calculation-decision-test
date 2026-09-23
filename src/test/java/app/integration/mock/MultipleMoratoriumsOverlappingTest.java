package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionTransformationTest {

    interface DecisionTransformationService {
        Map<String, Object> transform(Map<String, Object> payload);
    }

    @Mock
    private DecisionTransformationService transformationService;

    private Map<String, Object> overlappingMoratoriumsPayload;

    @BeforeEach
    void setUp() {
        overlappingMoratoriumsPayload = new HashMap<>();
        overlappingMoratoriumsPayload.put("claimId", "CLM-OVERLAP-001");
        overlappingMoratoriumsPayload.put("moratoriums", List.of(
                Map.of("startDate", "2023-01-01", "endDate", "2023-03-01", "type", "payment"),
                Map.of("startDate", "2023-02-15", "endDate", "2023-04-15", "type", "payment")
        ));
    }

    @Test
    void multiple_moratoriums_overlapping() {
        // Arrange: Define expected standardized output for overlapping moratoriums
        Map<String, Object> expectedStandardizedPayload = new HashMap<>();
        expectedStandardizedPayload.put("claimId", "CLM-OVERLAP-001");
        expectedStandardizedPayload.put("standardizedMoratoriums", List.of(
                Map.of("startDate", "2023-01-01", "endDate", "2023-04-15", "type", "payment", "overlapStatus", "MERGED")
        ));
        expectedStandardizedPayload.put("validationFlags", List.of("OVERLAPPING_PERIODS_DETECTED"));

        when(transformationService.transform(anyMap())).thenReturn(expectedStandardizedPayload);

        // Act
        Map<String, Object> result = transformationService.transform(overlappingMoratoriumsPayload);

        // Assert
        assertNotNull(result, "Transformed payload should not be null");
        assertEquals("CLM-OVERLAP-001", result.get("claimId"), "Claim ID should match");
        assertTrue(result.containsKey("standardizedMoratoriums"), "Should contain standardized moratoriums");
        assertEquals(1, ((List<?>) result.get("standardizedMoratoriums")).size(), "Overlapping moratoriums should be merged");
        assertEquals("MERGED", ((Map<?, ?>) ((List<?>) result.get("standardizedMoratoriums")).get(0)).get("overlapStatus"), "Should indicate merged overlap");
        assertTrue(((List<?>) result.get("validationFlags")).contains("OVERLAPPING_PERIODS_DETECTED"), "Should flag overlapping periods");

        verify(transformationService, times(1)).transform(anyMap());
    }
}
