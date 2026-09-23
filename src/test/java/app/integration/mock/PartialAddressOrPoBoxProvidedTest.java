package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionEnrichmentMockTest {

    @Mock
    private ClaimEnrichmentService enrichmentService;

    @InjectMocks
    private ClaimStandardizationProcessor claimProcessor;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimPayload = Map.of(
            "claimId", "CLM-POB-7890",
            "address", Map.of(
                "street", "PO Box 12345",
                "city", "Springfield",
                "state", "IL",
                "zip", "62704"
            ),
            "policyNumber", "POL-55667",
            "incidentDate", "2023-10-15"
        );
    }

    @Test
    void partial_address_or_po_box_provided() {
        // Arrange: Define expected enrichment decision for PO Box / partial address
        Map<String, Object> expectedEnrichedPayload = Map.of(
            "claimId", "CLM-POB-7890",
            "addressStandardized", false,
            "enrichmentDecision", "PARTIAL_ADDRESS_PO_BOX",
            "requiresManualReview", true,
            "validationWarnings", Map.of("addressType", "PO_BOX_DETECTED", "geocodeStatus", "SKIPPED")
        );

        when(enrichmentService.evaluateEnrichment(claimPayload)).thenReturn(expectedEnrichedPayload);

        // Act: Trigger enrichment processing
        Map<String, Object> actualResult = claimProcessor.processEnrichment(claimPayload);

        // Assert: Verify decision logic and mock invocation
        assertNotNull(actualResult, "Enrichment result must not be null");
        assertEquals("PARTIAL_ADDRESS_PO_BOX", actualResult.get("enrichmentDecision"));
        assertEquals(Boolean.FALSE, actualResult.get("addressStandardized"));
        assertEquals(Boolean.TRUE, actualResult.get("requiresManualReview"));
        @SuppressWarnings("unchecked")
        Map<String, String> warnings = (Map<String, String>) actualResult.get("validationWarnings");
        assertNotNull(warnings);
        assertEquals("PO_BOX_DETECTED", warnings.get("addressType"));
        
        verify(enrichmentService, times(1)).evaluateEnrichment(claimPayload);
        verifyNoMoreInteractions(enrichmentService);
    }
}
