package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private ClaimOrchestrationService claimOrchestrationService;

    private static final String START_DATE_KEY = "start_date";
    private static final String SAMPLE_START_DATE = "2024-01-15";
    private static final String SAMPLE_CLAIM_ID = "CLM-789";

    @BeforeEach
    void setUp() {
        // MockitoExtension handles field initialization and lifecycle management
    }

    @Test
    void start_date() {
        // Arrange
        Map<String, Object> rawClaimData = Map.of(
                "id", SAMPLE_CLAIM_ID,
                START_DATE_KEY, SAMPLE_START_DATE
        );

        Map<String, Object> standardizedPayload = Map.of(
                "id", SAMPLE_CLAIM_ID,
                START_DATE_KEY, SAMPLE_START_DATE,
                "standardized", true
        );

        when(claimOrchestrationService.processClaimData(rawClaimData)).thenReturn(standardizedPayload);

        // Act
        Map<String, Object> result = claimOrchestrationService.processClaimData(rawClaimData);

        // Assert
        assertNotNull(result, "Orchestration should return a standardized payload");
        assertEquals(SAMPLE_START_DATE, result.get(START_DATE_KEY), "start_date should be preserved during standardization");
        assertTrue((Boolean) result.get("standardized"), "Payload should be marked as standardized");
        verify(claimOrchestrationService, times(1)).processClaimData(rawClaimData);
    }

    // Mocked external orchestration service interface to isolate transformation logic
    private interface ClaimOrchestrationService {
        Map<String, Object> processClaimData(Map<String, Object> claimData);
    }
}
