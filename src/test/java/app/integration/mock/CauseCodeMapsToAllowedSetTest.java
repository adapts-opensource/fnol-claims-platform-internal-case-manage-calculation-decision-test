package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization:transformation:orchestration.
 * Verifies that cause codes are correctly mapped to the allowed set.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private ClaimDataStandardizationRulesEngine rulesEngine;

    @InjectMocks
    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @Test
    void cause_code_maps_to_allowed_set() {
        // Arrange
        String inputCauseCode = "RAW_CAUSE_99";
        String expectedStandardizedCode = "STD_CAUSE_01";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("causeCode", inputCauseCode);
        inputPayload.put("claimId", "claim-123");

        // Mock the rules engine to simulate mapping logic
        when(rulesEngine.mapCauseCodeToAllowedSet(inputCauseCode))
                .thenReturn(expectedStandardizedCode);

        // Act
        Map<String, Object> resultPayload = orchestrationService.transformPayload(inputPayload);

        // Assert
        assertNotNull(resultPayload, "Result payload should not be null");
        assertEquals(expectedStandardizedCode, resultPayload.get("causeCode"),
                "Cause code should be mapped to the allowed set");
        assertEquals("claim-123", resultPayload.get("claimId"),
                "Other fields should remain intact");

        verify(rulesEngine).mapCauseCodeToAllowedSet(inputCauseCode);
    }
}
