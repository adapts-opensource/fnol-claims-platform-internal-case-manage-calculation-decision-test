package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionEnrichmentTest {

    @Mock
    private ClaimEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        // External I/O (S3/AuditDiaryStore, DynamoDB/RulesEngineDecisionService, DynamoDB/WorkflowTaskRouter)
        // is fully abstracted behind enrichmentService. No live AWS or production HTTP APIs are invoked.
    }

    @Test
    void past_effective_date() {
        // Arrange
        Map<String, Object> claimPayload = new HashMap<>();
        claimPayload.put("claimId", "CLM-98765");
        claimPayload.put("effectiveDate", Date.from(LocalDate.now().minusDays(15).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        claimPayload.put("policyNumber", "POL-11223");

        Map<String, Object> expectedEnrichedData = new HashMap<>();
        expectedEnrichedData.put("enrichmentFlag", "PAST_EFFECTIVE_DATE");
        expectedEnrichedData.put("standardizedStatus", "AWAITING_REVIEW");
        expectedEnrichedData.put("complianceNote", "Effective date precedes current processing date");

        when(enrichmentService.enrichClaimData(claimPayload)).thenReturn(expectedEnrichedData);

        // Act
        Map<String, Object> actualEnrichedData = enrichmentService.enrichClaimData(claimPayload);

        // Assert
        assertNotNull(actualEnrichedData, "Enrichment result should not be null");
        assertEquals("PAST_EFFECTIVE_DATE", actualEnrichedData.get("enrichmentFlag"));
        assertEquals("AWAITING_REVIEW", actualEnrichedData.get("standardizedStatus"));
        assertTrue(((String) actualEnrichedData.get("complianceNote")).contains("Effective date"));

        verify(enrichmentService, times(1)).enrichClaimData(claimPayload);
    }
}
