package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// JUnit 5 test class with @Test methods
@ExtendWith(MockitoExtension.class)
class AuditStoreUnavailableFallbackToBackupTest {

    @Mock
    private AuditStore auditStore;

    @Mock
    private BackupStore backupStore;

    private DecisionTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = new DecisionTransformationService(auditStore, backupStore);
    }

    @Test
    void audit_store_unavailable_fallback_to_backup() {
        // Arrange
        String decisionId = "DEC-TRANS-001";
        String transformedPayload = "{\"decisionId\":\"DEC-TRANS-001\",\"status\":\"approved\",\"insuredId\":\"INS-99\"}";

        when(auditStore.storeAsync(decisionId, transformedPayload))
                .thenThrow(new RuntimeException("ServiceUnavailable: Audit store endpoint timed out"));

        // Act
        assertDoesNotThrow(() -> transformationService.processAndRecord(decisionId, transformedPayload));

        // Assert
        verify(auditStore, times(1)).storeAsync(decisionId, transformedPayload);
        verify(backupStore, times(1)).storeAsync(decisionId, transformedPayload);
        verifyNoMoreInteractions(auditStore, backupStore);
    }

    // Internal interfaces representing external I/O contracts for mocking
    interface AuditStore {
        void storeAsync(String decisionId, String payload);
    }

    interface BackupStore {
        void storeAsync(String decisionId, String payload);
    }

    // Service under test handling the fallback logic
    class DecisionTransformationService {
        private final AuditStore auditStore;
        private final BackupStore backupStore;

        DecisionTransformationService(AuditStore auditStore, BackupStore backupStore) {
            this.auditStore = auditStore;
            this.backupStore = backupStore;
        }

        void processAndRecord(String decisionId, String payload) {
            try {
                auditStore.storeAsync(decisionId, payload);
            } catch (Exception e) {
                backupStore.storeAsync(decisionId, payload);
            }
        }
    }
}
