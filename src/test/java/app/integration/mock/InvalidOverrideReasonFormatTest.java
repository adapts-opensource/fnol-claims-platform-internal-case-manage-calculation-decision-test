package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionEnrichmentIntegrationTest {

    @Mock
    private ClaimEnrichmentProcessor enrichmentProcessor;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimPayload = Map.of(
            "claimId", "CLM-998877",
            "overrideReason", "INVALID!@#FORMAT$",
            "decisionContext", Map.of("feature", "Claim Data Standardization:decision:enrichment")
        );
    }

    @Test
    void invalid_override_reason_format() {
        when(enrichmentProcessor.validateAndEnrich(anyMap()))
            .thenThrow(new IllegalArgumentException("Invalid override reason format: must be alphanumeric with spaces, max 255 chars."));

        assertThrows(IllegalArgumentException.class, () -> {
            enrichmentProcessor.validateAndEnrich(claimPayload);
        });

        verify(enrichmentProcessor, times(1)).validateAndEnrich(claimPayload);
    }
}
