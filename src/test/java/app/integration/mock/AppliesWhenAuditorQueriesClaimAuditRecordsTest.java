package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class AppliesWhenAuditorQueriesClaimAuditRecordsTest {

    @Mock
    private ClaimAuditQueryService claimAuditQueryService;

    @Mock
    private DecisionValidationEngine decisionValidationEngine;

    @InjectMocks
    private ClaimDataStandardizationEnrichmentService claimDataStandardizationEnrichmentService;

    @BeforeEach
    void setUp() {
        // Ensure isolated mock state for each test execution (thread-safe)
        clearMocks(claimAuditQueryService, decisionValidationEngine);
    }

    @Test
    void applies_when_auditor_queries_claim_audit_records() {
        // Arrange
        String auditorId = "auditor-123";
        String claimId = "claim-789";
        String recordId = UUID.randomUUID().toString();

        Map<String, Object> rawAuditRecord = Map.of(
            "id", recordId,
            "claimId", claimId,
            "status", "PENDING_AUDIT",
            "payload", Map.of("type", "FNOL", "version", "1.0")
        );

        Map<String, Object> expectedEnrichedRecord = Map.of(
            "id", recordId,
            "claimId", claimId,
            "status", "VALIDATED",
            "payload", Map.of("type", "FNOL", "version", "1.0", "standardized", true),
            "validatedBy", "enrichment-validator",
            "timestamp", System.currentTimeMillis()
        );

        when(claimAuditQueryService.fetchAuditRecords(auditorId, claimId))
            .thenReturn(Collections.singletonList(rawAuditRecord));
        when(decisionValidationEngine.validateAndEnrich(anyMap()))
            .thenReturn(expectedEnrichedRecord);

        // Act
        List<Map<String, Object>> result = claimDataStandardizationEnrichmentService.processAuditRecords(auditorId, claimId);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertTrue(result.get(0).containsKey("validatedBy"));
        assertEquals("VALIDATED", result.get(0).get("status"));

        // Verify interactions (mocked I/O, no live AWS/HTTP calls)
        verify(claimAuditQueryService, times(1)).fetchAuditRecords(auditorId, claimId);
        verify(decisionValidationEngine, times(1)).validateAndEnrich(rawAuditRecord);
        verifyNoMoreInteractions(claimAuditQueryService, decisionValidationEngine);
    }
}
