package app.integration.mock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationEnrichmentDecisionTest {

    @Mock
    private ClaimDataStandardizationDecisionEnrichmentService enrichmentService;

    @Mock
    private DocumentMediaStoreClient documentMediaStore;

    @Mock
    private PolicyClaimDataStoreClient policyClaimDataStore;

    @InjectMocks
    private ClaimDataStandardizationDecisionProcessor decisionProcessor;

    @Test
    void applies_when_mobile_fnol_submission_initiated() {
        // Arrange
        String submissionId = "mobile-fnol-123";
        Map<String, Object> initialPayload = Map.of(
            "id", submissionId,
            "channel", "MOBILE_APP",
            "status", "SUBMITTED",
            "enrichmentPhase", "DECISION"
        );

        Map<String, Object> expectedEnrichedPayload = Map.of(
            "id", submissionId,
            "channel", "MOBILE_APP",
            "status", "SUBMITTED",
            "enrichmentPhase", "DECISION",
            "decision", "ENRICHED",
            "decisionReason", "MOBILE_FNOL_INITIATED",
            "standardizationFlags", Map.of("autoStandardize", true)
        );

        when(enrichmentService.applyDecisionEnrichment(anyMap())).thenReturn(expectedEnrichedPayload);

        // Act
        Map<String, Object> resultPayload = decisionProcessor.processEnrichment(initialPayload);

        // Assert
        assertNotNull(resultPayload);
        assertEquals("ENRICHED", resultPayload.get("decision"));
        assertEquals("MOBILE_FNOL_INITIATED", resultPayload.get("decisionReason"));
        assertTrue((Boolean) resultPayload.get("standardizationFlags").get("autoStandardize"));

        // Verify service interaction
        verify(enrichmentService, times(1)).applyDecisionEnrichment(initialPayload);

        // Verify external I/O is mocked and not called directly
        verifyNoInteractions(documentMediaStore, policyClaimDataStore);
    }
}
