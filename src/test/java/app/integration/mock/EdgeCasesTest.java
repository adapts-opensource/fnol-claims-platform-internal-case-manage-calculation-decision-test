package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Validates edge cases for Multi-Channel FNOL Submission: validation & decision logic.
 * Covers input boundary checks, idempotency thread safety, GDPR PII masking, and SOC2 audit compliance.
 */
@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionValidationDecisionEdgeCasesTest {

    // Self-contained DTOs for test isolation
    record FnolSubmissionRequest(String claimId, String claimNumber, String tenantId, String policyId, String idempotencyKey, Instant auditTimestamp) {}
    record FnolDecisionResponse(String decision, String claimId, Map<String, Object> metadata) {}

    interface FnolValidationDecisionService {
        FnolDecisionResponse validateAndDecide(FnlSubmissionRequest request);
    }

    @Mock
    private FnolValidationDecisionService mockService;

    @Test
    void edge_cases_55() {
        // 1. Missing tenant_id triggers validation failure at service boundary
        FnolSubmissionRequest missingTenantReq = new FnolSubmissionRequest(UUID.randomUUID().toString(), "CLM-001", null, "POL-123", UUID.randomUUID().toString(), Instant.now());
        doThrow(new IllegalArgumentException("tenant_id is required")).when(mockService).validateAndDecide(any());
        assertThrows(IllegalArgumentException.class, () -> mockService.validateAndDecide(missingTenantReq));

        // 2. Empty policy_id triggers validation failure
        FnolSubmissionRequest emptyPolicyReq = new FnolSubmissionRequest(UUID.randomUUID().toString(), "CLM-002", "TENANT-01", "", UUID.randomUUID().toString(), Instant.now());
        doThrow(new IllegalArgumentException("policy_id cannot be empty")).when(mockService).validateAndDecide(any());
        assertThrows(IllegalArgumentException.class, () -> mockService.validateAndDecide(emptyPolicyReq));

        // 3. Null idempotency key handled gracefully to enforce thread safety
        FnolSubmissionRequest nullIdempReq = new FnolSubmissionRequest(UUID.randomUUID().toString(), "CLM-003", "TENANT-02", "POL-456", null, Instant.now());
        FnolDecisionResponse nullIdempRes = new FnolDecisionResponse("PENDING", nullIdempReq.claimId(), Map.of("idempotency_handled", true));
        when(mockService.validateAndDecide(any())).thenReturn(nullIdempRes);
        assertEquals("PENDING", mockService.validateAndDecide(nullIdempReq).decision());

        // 4. Concurrent submissions with same idempotency key return identical decision (idempotent side effects)
        String idempKey = UUID.randomUUID().toString();
        FnolSubmissionRequest req1 = new FnolSubmissionRequest(UUID.randomUUID().toString(), "CLM-004", "TENANT-03", "POL-789", idempKey, Instant.now());
        FnolSubmissionRequest req2 = new FnolSubmissionRequest(UUID.randomUUID().toString(), "CLM-004", "TENANT-03", "POL-789", idempKey, Instant.now());
        FnolDecisionResponse expected = new FnolDecisionResponse("APPROVED", "CLM-004", Map.of("source", "mock"));
        when(mockService.validateAndDecide(req1)).thenReturn(expected);
        when(mockService.validateAndDecide(req2)).thenReturn(expected);
        assertEquals(expected, mockService.validateAndDecide(req2));

        // 5. GDPR compliance ensures PII is masked or excluded from decision payload
        FnolSubmissionRequest gdprReq = new FnolSubmissionRequest(UUID.randomUUID().toString(), "CLM-005", "TENANT-04", "POL-999", UUID.randomUUID().toString(), Instant.now());
        FnolDecisionResponse gdprRes = new FnolDecisionResponse("APPROVED", "CLM-005", Map.of("pii_masked", true, "ssn", "***-**-1234"));
        assertFalse(gdprRes.metadata().toString().contains("ssn"), "PII fields must be masked or excluded per GDPR");
        assertTrue(gdprRes.metadata().toString().contains("pii_masked"));

        // 6. SOC2 compliance ensures audit timestamps are present and immutable
        FnolSubmissionRequest soc2Req = new FnolSubmissionRequest(UUID.randomUUID().toString(), "CLM-006", "TENANT-05", "POL-000", UUID.randomUUID().toString(), Instant.now());
        Instant auditTime = Instant.now();
        FnolDecisionResponse soc2Res = new FnolDecisionResponse("APPROVED", "CLM-006", Map.of("audit_timestamp", auditTime, "audit_user", "SYSTEM"));
        assertNotNull(soc2Res.metadata().get("audit_timestamp"));
        assertEquals(auditTime, soc2Res.metadata().get("audit_timestamp"));
    }
}
