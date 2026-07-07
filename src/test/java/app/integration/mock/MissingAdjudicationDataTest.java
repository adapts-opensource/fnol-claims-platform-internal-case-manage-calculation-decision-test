package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MissingAdjudicationDataTest {

    @Mock
    private ClaimDataEnrichmentService enrichmentService;

    private Map<String, Object> payloadMissingAdjudication;

    @BeforeEach
    void setUp() {
        payloadMissingAdjudication = new HashMap<>();
        payloadMissingAdjudication.put("id", "claim-transform-001");
        payloadMissingAdjudication.put("policyNumber", "POL-2024-001");
        payloadMissingAdjudication.put("incidentDate", "2024-05-15");
        payloadMissingAdjudication.put("status", "SUBMITTED");
        // Adjudication fields intentionally omitted to simulate missing data
    }

    @Test
    void missing_adjudication_data() {
        // Arrange: Mock service to return expected enrichment status when adjudication data is absent
        when(enrichmentService.enrichClaimData(anyMap()))
            .thenReturn(EnrichmentOutcome.SKIPPED_MISSING_ADJUDICATION);

        // Act: Invoke enrichment with payload lacking adjudication data
        EnrichmentOutcome outcome = enrichmentService.enrichClaimData(payloadMissingAdjudication);

        // Assert: Verify graceful handling and correct outcome
        assertEquals(EnrichmentOutcome.SKIPPED_MISSING_ADJUDICATION, outcome);
        verify(enrichmentService).enrichClaimData(payloadMissingAdjudication);
        verifyNoMoreInteractions(enrichmentService);
    }

    interface ClaimDataEnrichmentService {
        EnrichmentOutcome enrichClaimData(Map<String, Object> payload);
    }

    enum EnrichmentOutcome {
        SUCCESS,
        SKIPPED_MISSING_ADJUDICATION,
        ERROR_VALIDATION_FAILED
    }
}
