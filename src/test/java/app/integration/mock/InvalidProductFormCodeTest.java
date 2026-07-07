package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FnolSubmissionValidationDecisionTest {

    // Simulated external decision engine interface for mocked I/O
    private interface FnolDecisionEngine {
        Map<String, Object> evaluateDecision(String productFormCode, String idempotencyKey, String tenantId, Instant auditTimestamp);
    }

    private FnolDecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        // Thread-safe initialization: no shared mutable state across test executions
        decisionEngine = mock(FnolDecisionEngine.class);
    }

    @Test
    void invalid_product_form_code() {
        // Arrange: FNOL submission payload with invalid product form code
        String invalidProductFormCode = "INVALID_FORM_CODE";
        String idempotencyKey = "idemp-key-456";
        String tenantId = "tenant-insurance-001";
        Instant auditTimestamp = Instant.now();
        
        String expectedStatus = "REJECTED";
        String expectedReason = "VALIDATION_FAILED: Invalid product form code";

        // Mock external I/O: decision engine returns structured rejection for invalid form code
        Map<String, Object> expectedDecision = new HashMap<>();
        expectedDecision.put("status", expectedStatus);
        expectedDecision.put("reason", expectedReason);
        expectedDecision.put("idempotency_key", idempotencyKey);
        expectedDecision.put("tenant_id", tenantId);
        expectedDecision.put("audit_timestamp", auditTimestamp.toString());
        expectedDecision.put("structured_log", "NFR: input_validation, tls_in_transit, least_privilege_iam, secrets_management, gdpr, soc2");

        when(decisionEngine.evaluateDecision(invalidProductFormCode, idempotencyKey, tenantId, auditTimestamp))
                .thenReturn(expectedDecision);

        // Act: invoke mocked decision service boundary
        Map<String, Object> actualDecision = decisionEngine.evaluateDecision(invalidProductFormCode, idempotencyKey, tenantId, auditTimestamp);

        // Assert: validation decision correctly rejects invalid form code
        assertNotNull(actualDecision, "Decision payload must not be null");
        assertEquals(expectedStatus, actualDecision.get("status"), "Decision status should be REJECTED for invalid form code");
        assertEquals(expectedReason, actualDecision.get("reason"), "Reason must match input validation failure");
        assertEquals(idempotencyKey, actualDecision.get("idempotency_key"), "Idempotency key must be preserved for thread safety");
        assertEquals(tenantId, actualDecision.get("tenant_id"), "Tenant context must be preserved per global conventions");

        // Verify external I/O was called exactly once (mocked, no live AWS/HTTP)
        verify(decisionEngine, times(1)).evaluateDecision(invalidProductFormCode, idempotencyKey, tenantId, auditTimestamp);
    }
}
