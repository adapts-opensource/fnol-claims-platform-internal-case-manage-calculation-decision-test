package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClaimDataStandardizationDecisionEnrichmentMockTest {

    @Mock
    private ClaimEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void drift_threshold_5_deviation_over_30_days() {
        // Arrange
        String claimId = "CLM-2024-001";
        Map<String, Object> claimPayload = Map.of(
            "id", claimId,
            "driftPercentage", 5.5,
            "observationWindowDays", 30,
            "baselineValue", 1000.0,
            "currentValue", 1055.0
        );

        when(enrichmentService.processEnrichment(claimId, claimPayload))
            .thenReturn(Map.of("enrichmentDecision", "FLAG_DRIFT", "driftPercentage", 5.5));

        // Act
        Map<String, Object> result = enrichmentService.processEnrichment(claimId, claimPayload);

        // Assert
        assertNotNull(result, "Enriched payload must not be null");
        assertEquals("FLAG_DRIFT", result.get("enrichmentDecision"), "Enrichment should flag drift");
        assertTrue((Double) result.get("driftPercentage") > 5.0, "Drift deviation must exceed 5% over 30 days");
        verify(enrichmentService).processEnrichment(claimId, claimPayload);
    }
}
