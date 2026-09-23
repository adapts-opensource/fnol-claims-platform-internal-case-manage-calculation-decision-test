package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurposeSupportSpecialistInResolvingMultipleOrNoPolicyMatchesWithAuditTrailTest {

    @Mock
    private PolicyMatchingService policyMatchingService;
    @Mock
    private AuditDiaryStore auditDiaryStore;

    private ClaimEnrichmentService enrichmentService;
    private String claimId;
    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimEnrichmentService(policyMatchingService, auditDiaryStore);
        claimId = UUID.randomUUID().toString();
        claimPayload = Map.of("claimId", claimId, "policyNumber", "POL-123", "status", "OPEN");
    }

    @Test
    void purpose_support_specialist_in_resolving_multiple_or_no_policy_matches_with_audit_trail() {
        // Scenario 1: No policy matches found
        when(policyMatchingService.findMatches(any(Map.class))).thenReturn(Collections.emptyList());
        when(auditDiaryStore.writeAudit(anyString(), anyString(), any(Map.class))).thenReturn("s3://audit-bucket/" + claimId + ".json");

        Map<String, Object> resultNoMatches = enrichmentService.enrichClaimData(claimPayload);

        assertEquals("NEEDS_MANUAL_REVIEW", resultNoMatches.get("matchStatus"));
        assertEquals(0, ((Number) resultNoMatches.get("policyMatchCount")).intValue());
        verifyAuditTrail("NO_MATCHES_FOUND");

        // Scenario 2: Multiple policy matches found
        List<Map<String, Object>> multipleMatches = List.of(
            Map.of("policyId", "P1", "status", "ACTIVE"),
            Map.of("policyId", "P2", "status", "ACTIVE")
        );
        when(policyMatchingService.findMatches(any(Map.class))).thenReturn(multipleMatches);
        when(auditDiaryStore.writeAudit(anyString(), anyString(), any(Map.class))).thenReturn("s3://audit-bucket/" + claimId + ".json");

        Map<String, Object> resultMultipleMatches = enrichmentService.enrichClaimData(claimPayload);

        assertEquals("NEEDS_MANUAL_REVIEW", resultMultipleMatches.get("matchStatus"));
        assertEquals(2, ((Number) resultMultipleMatches.get("policyMatchCount")).intValue());
        verifyAuditTrail("MULTIPLE_MATCHES_FOUND");
    }

    private void verifyAuditTrail(String expectedReasonCode) {
        ArgumentCaptor<Map<String, Object>> auditCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditDiaryStore, times(1)).writeAudit(eq("POLICY_MATCH_RESULT"), eq(claimId), auditCaptor.capture());
        Map<String, Object> capturedAudit = auditCaptor.getValue();
        assertNotNull(capturedAudit.get("auditTrail"));
        assertEquals(expectedReasonCode, capturedAudit.get("reasonCode"));
        assertTrue(((String) capturedAudit.get("auditTrail")).contains("enrichment"));
    }

    // Minimal service interfaces for test isolation
    interface PolicyMatchingService {
        List<Map<String, Object>> findMatches(Map<String, Object> claimData);
    }

    interface AuditDiaryStore {
        String writeAudit(String eventType, String entityId, Map<String, Object> metadata);
    }

    // Service under test (thread-safe, uses local state only)
    static class ClaimEnrichmentService {
        private final PolicyMatchingService policyMatchingService;
        private final AuditDiaryStore auditDiaryStore;

        ClaimEnrichmentService(PolicyMatchingService policyMatchingService, AuditDiaryStore auditDiaryStore) {
            this.policyMatchingService = policyMatchingService;
            this.auditDiaryStore = auditDiaryStore;
        }

        Map<String, Object> enrichClaimData(Map<String, Object> claimData) {
            String claimId = (String) claimData.get("claimId");
            List<Map<String, Object>> matches = policyMatchingService.findMatches(claimData);
            int matchCount = matches.size();
            String status;

            if (matchCount == 0) {
                status = "NEEDS_MANUAL_REVIEW";
                logAudit(claimId, "NO_MATCHES_FOUND");
            } else if (matchCount > 1) {
                status = "NEEDS_MANUAL_REVIEW";
                logAudit(claimId, "MULTIPLE_MATCHES_FOUND");
            } else {
                status = "RESOLVED";
                logAudit(claimId, "SINGLE_MATCH_FOUND");
            }

            return Map.of(
                "claimId", claimId,
                "matchStatus", status,
                "policyMatchCount", matchCount,
                "resolvedPolicyId", matchCount == 1 ? matches.get(0).get("policyId") : null
            );
        }

        private void logAudit(String claimId, String reasonCode) {
            Map<String, Object> metadata = Map.of(
                "auditTrail", "enrichment_process_completed",
                "reasonCode", reasonCode,
                "timestamp", System.currentTimeMillis()
            );
            auditDiaryStore.writeAudit("POLICY_MATCH_RESULT", claimId, metadata);
        }
    }
}
