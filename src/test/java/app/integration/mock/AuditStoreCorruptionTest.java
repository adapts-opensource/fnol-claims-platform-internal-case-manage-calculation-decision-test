package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditStoreCorruptionTest {

    @Mock
    private AuditStore auditStore;

    @InjectMocks
    private FnolValidationDecisionService service;

    @BeforeEach
    void setUp() {
        // Reset mocks to ensure thread-safe concurrent execution
    }

    @Test
    void audit_store_corruption() {
        // Arrange
        String claimId = "CLM-102030";
        String tenantId = "newco_tenant_01";
        String idempotencyKey = "idem-key-uuid-123";

        // Simulate audit store corruption (e.g., malformed DynamoDB item or checksum mismatch)
        when(auditStore.retrieveAuditRecord(claimId, tenantId, idempotencyKey))
                .thenThrow(new RuntimeException("AUDIT_STORE_CORRUPTION: Payload integrity verification failed"));

        // Act & Assert
        // Validation/Decision layer must catch corruption, halt processing, and fail fast
        // Preserves SOC2 audit trails and GDPR data minimization by stopping PII flow
        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.validateAndDecide(claimId, tenantId, idempotencyKey)
        );

        // Verify strict interaction contract
        verify(auditStore, times(1)).retrieveAuditRecord(claimId, tenantId, idempotencyKey);
        verifyNoMoreInteractions(auditStore);

        // Assert corruption context is preserved for structured logging and observability
        assertTrue(thrown.getMessage().startsWith("AUDIT_STORE_CORRUPTION"));
    }
}
