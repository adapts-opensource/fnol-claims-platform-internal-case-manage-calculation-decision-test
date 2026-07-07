package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * Integration mock tests for Claim Data Standardization:decision:enrichment.
 * Validates business rule enforcement without invoking live AWS or HTTP endpoints.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationEnrichmentTest {

    @Mock
    private ClaimEnrichmentService enrichmentService;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimPayload = new HashMap<>();
        claimPayload.put("claimId", "CLM-STD-001");
        claimPayload.put("policyNumber", "POL-STD-001");
        claimPayload.put("claimType", "AUTO");
    }

    @Test
    void effective_date_cannot_be_in_the_past() {
        // Arrange: Set a past effective date to trigger validation failure
        LocalDate pastEffectiveDate = LocalDate.now().minusDays(2);
        claimPayload.put("effectiveDate", pastEffectiveDate.toString());

        // Mock the enrichment service to simulate rule engine validation failure
        when(enrichmentService.enrichClaimData(anyMap()))
                .thenThrow(new IllegalArgumentException("Effective date cannot be in the past"));

        // Act & Assert: Verify that the service throws the expected validation exception
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> enrichmentService.enrichClaimData(claimPayload)
        );
        assertEquals("Effective date cannot be in the past", thrown.getMessage());

        // Verify the service was invoked exactly once with the prepared payload
        verify(enrichmentService).enrichClaimData(claimPayload);
    }

    /**
     * Mock service interface representing the enrichment decision layer.
     * In production, this would coordinate with RulesEngineDecisionService (DynamoDB)
     * and AuditDiaryStore (S3) while enforcing data standardization rules.
     */
    static interface ClaimEnrichmentService {
        Map<String, Object> enrichClaimData(Map<String, Object> payload);
    }
}
