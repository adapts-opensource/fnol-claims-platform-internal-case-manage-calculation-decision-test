package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 test class for feature: Claim Data Standardization:enrichment:validation
 * Validates that statutory notices are accurately included during claim payload enrichment.
 * Mocks S3 and DynamoDB I/O contracts to ensure zero production dependencies.
 */
@ExtendWith(MockitoExtension.class)
public class StatutoryNoticesIncludedAccuratelyTest {

    @Mock
    private ClaimDataEnrichmentService enrichmentService;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        // Aligns with claim_data_standardization_decision_validation entity structure
        testPayload = new HashMap<>();
        testPayload.put("id", "CLM-STD-10293");
        testPayload.put("payload", Map.of(
                "statutoryNotices", Map.of(
                        "type", "STATE_REGULATORY_DISCLOSURE",
                        "included", true,
                        "format", "ISO_8601",
                        "verified", true,
                        "checksum", "x9y8z7w6"
                ),
                "claimMetadata", Map.of(
                        "source", "FNOL_WEB_PORTAL",
                        "timestamp", "2024-05-15T08:30:00Z"
                )
        ));
    }

    @Test
    void statutory_notices_included_accurately() {
        // Arrange: Mock S3 document retrieval and DynamoDB item persistence
        when(enrichmentService.fetchReferenceDocument(anyString(), anyString())).thenReturn(Map.of(
                "bucket_name", "Document & Media Store-bucket",
                "object_key_pattern", "Document & Media Store/CLM-STD-10293.json",
                "contentHash", "x9y8z7w6"
        ));

        when(enrichmentService.persistStandardizedItem(anyString(), anyMap())).thenReturn(Map.of(
                "table_name", "Policy & Claim Data Store_table",
                "partition_key", "pk",
                "item_payload", testPayload
        ));

        // Act: Execute the enrichment and validation workflow
        Map<String, Object> standardizedResult = enrichmentService.enrichAndValidateStatutoryNotices(testPayload);

        // Assert: Verify payload structure and validation outcome
        assertNotNull(standardizedResult);
        assertEquals("CLM-STD-10293", standardizedResult.get("id"));
        assertTrue((Boolean) standardizedResult.get("validationPassed"));

        // Assert: Verify statutory notices are accurately preserved and validated
        Map<String, Object> notices = (Map<String, Object>) ((Map<?, ?>) standardizedResult.get("payload")).get("statutoryNotices");
        assertNotNull(notices);
        assertTrue((Boolean) notices.get("included"));
        assertEquals("ISO_8601", notices.get("format"));
        assertTrue((Boolean) notices.get("verified"));
        assertEquals("x9y8z7w6", notices.get("checksum"));

        // Assert: Verify infra I/O contracts were invoked exactly once
        verify(enrichmentService, times(1)).fetchReferenceDocument(anyString(), anyString());
        verify(enrichmentService, times(1)).persistStandardizedItem(anyString(), anyMap());
    }
}
