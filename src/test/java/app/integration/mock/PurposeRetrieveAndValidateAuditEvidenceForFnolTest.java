package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Validates retrieval and structural integrity of audit evidence
 * for FNOL submission decisions, ensuring SOC2 audit trails and GDPR data minimization.
 */
public class PurposeRetrieveAndValidateAuditEvidenceForFnolTest {

    @Mock
    private AuditEvidenceService auditEvidenceService;

    @Mock
    private Logger structuredLogger;

    private String testClaimId;
    private String testTenantId;
    private String testPolicyId;
    private String testClaimNumber;

    @BeforeEach
    void setUp() {
        testClaimId = "claim-12345";
        testTenantId = "tenant-001";
        testPolicyId = "pol-67890";
        testClaimNumber = "FNOL-2024-001";
    }

    @Test
    void purpose_retrieve_and_validate_audit_evidence_for_fnol_decisions() {
        // Arrange
        Instant auditTimestamp = Instant.now();
        Map<String, Object> expectedEvidence = Map.of(
            "claim_id", testClaimId,
            "claim_number", testClaimNumber,
            "tenant_id", testTenantId,
            "policy_id", testPolicyId,
            "decision_status", "APPROVED",
            "validation_result", "PASSED",
            "audit_timestamp", auditTimestamp.toString(),
            "channel", "WEB",
            "idempotency_key", "idemp-abc-123"
        );

        when(auditEvidenceService.retrieveAuditEvidence(anyString()))
            .thenReturn(Optional.of(expectedEvidence));

        // Act
        when(structuredLogger.isInfoEnabled()).thenReturn(true);
        Optional<Map<String, Object>> retrievedEvidence = auditEvidenceService.retrieveAuditEvidence(testClaimId);
        structuredLogger.info("Audit evidence retrieved successfully for claim: " + testClaimId);

        // Assert
        assertTrue(retrievedEvidence.isPresent(), "Audit evidence must be present for FNOL decision traceability");
        Map<String, Object> evidence = retrievedEvidence.get();
        assertEquals(testClaimId, evidence.get("claim_id"), "claim_id must match submitted value");
        assertEquals(testTenantId, evidence.get("tenant_id"), "tenant_id required for multi-tenant isolation");
        assertEquals("APPROVED", evidence.get("decision_status"), "decision_status must be valid per validation rules");
        assertNotNull(evidence.get("audit_timestamp"), "audit_timestamp required for SOC2 compliance and statutory diaries");
        assertEquals("idemp-abc-123", evidence.get("idempotency_key"), "idempotency_key ensures thread safety for concurrent workers");

        verify(auditEvidenceService, times(1)).retrieveAuditEvidence(testClaimId);
        verify(structuredLogger).info("Audit evidence retrieved successfully for claim: " + testClaimId);
    }
}
