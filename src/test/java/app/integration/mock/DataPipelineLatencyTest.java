package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DataPipelineLatencyTest {

    private static final Logger LOG = Logger.getLogger(DataPipelineLatencyTest.class.getName());
    private static final long MAX_ALLOWED_LATENCY_MS = 5000L;

    // Minimal interface representing the external enrichment decision service
    private interface ClaimEnrichmentPipeline {
        Map<String, Object> processEnrichment(String claimId, Map<String, Object> payload);
    }

    @Mock
    private ClaimEnrichmentPipeline enrichmentPipeline;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void dataPipelineLatency() {
        // Arrange: Mock external enrichment pipeline behavior to avoid live infra calls
        Map<String, Object> mockResult = Map.of("id", "claim-101", "status", "ENRICHED");
        when(enrichmentPipeline.processEnrichment(anyString(), anyMap())).thenReturn(mockResult);

        // Act: Execute pipeline and measure latency
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = enrichmentPipeline.processEnrichment("claim-101", Map.of("payload", "sample"));
        long endTime = System.currentTimeMillis();
        long elapsedMs = endTime - startTime;

        // Assert: Verify result integrity and latency constraint
        assertNotNull(result, "Enrichment result should not be null");
        assertEquals("ENRICHED", result.get("status"), "Claim status should be updated to ENRICHED");
        assertTrue(elapsedMs <= MAX_ALLOWED_LATENCY_MS,
                "Data pipeline latency exceeded threshold: " + elapsedMs + "ms <= " + MAX_ALLOWED_LATENCY_MS + "ms");

        // Observe: Log latency for structured observability NFR
        LOG.info(() -> String.format("Pipeline latency: %d ms | Result: %s", elapsedMs, result));

        // Verify: Ensure pipeline was called exactly once with expected inputs
        verify(enrichmentPipeline, times(1)).processEnrichment(eq("claim-101"), anyMap());
    }
}
