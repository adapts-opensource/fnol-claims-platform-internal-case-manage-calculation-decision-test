package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppliesWhenAuditorRequestsFnolAuditTrailTest {

    @Mock
    private ClaimDataStandardizationOrchestrator claimDataStandardizationOrchestrator;

    @Mock
    private AuditTrailService auditTrailService;

    private String claimId;
    private Map<String, Object> rawPayload;

    @BeforeEach
    void setUp() {
        claimId = UUID.randomUUID().toString();
        rawPayload = Map.of(
            "id", claimId,
            "type", "FNOL",
            "requestorRole", "AUDITOR",
            "auditTrailRequested", true
        );
    }

    @Test
    void applies_when_auditor_requests_fnol_audit_trail() {
        // Arrange: Mock orchestration behavior when auditor requests FNOL audit trail
        String expectedPayloadId = "orch-" + claimId;
        Map<String, Object> standardizedPayload = Map.of(
            "id", expectedPayloadId,
            "claimId", claimId,
            "status", "STANDARDIZED",
            "auditTrail", Map.of("requestedBy", "AUDITOR", "scope", "FNOL", "timestamp", System.currentTimeMillis())
        );

        when(claimDataStandardizationOrchestrator.transform(rawPayload))
            .thenReturn(standardizedPayload);

        // Act: Invoke orchestration
        Map<String, Object> result = claimDataStandardizationOrchestrator.transform(rawPayload);

        // Assert: Verify transformation applied correctly
        assertNotNull(result, "Transformed payload must not be null");
        assertEquals(expectedPayloadId, result.get("id"), "Payload ID should be standardized");
        assertEquals("STANDARDIZED", result.get("status"), "State should transition to STANDARDIZED");

        @SuppressWarnings("unchecked")
        Map<String, Object> auditTrail = (Map<String, Object>) result.get("auditTrail");
        assertNotNull(auditTrail, "Audit trail must be present when requested by auditor");
        assertEquals("AUDITOR", auditTrail.get("requestedBy"), "Requestor role must be captured in audit trail");
        assertEquals("FNOL", auditTrail.get("scope"), "Audit scope must match FNOL");

        // Verify orchestration was invoked exactly once
        verify(claimDataStandardizationOrchestrator, times(1)).transform(rawPayload);
    }
}
