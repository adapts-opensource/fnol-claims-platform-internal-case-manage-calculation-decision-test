package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchemaMismatchRejectUpdateLogErrorTest {

    interface ClaimDataStore {
        void putItem(String partitionKey, Map<String, Object> item);
    }

    interface DocumentManagement {
        String uploadDocument(String bucketName, String keyPattern, byte[] data);
    }

    interface RulesTriage {
        Map<String, Object> validatePayload(Map<String, Object> payload);
    }

    @Mock
    private ClaimDataStore claimDataStore;
    @Mock
    private DocumentManagement documentManagement;
    @Mock
    private RulesTriage rulesTriage;
    @Mock
    private Logger structuredLogger;

    private ClaimStateTransitionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimStateTransitionOrchestrator(claimDataStore, documentManagement, rulesTriage, structuredLogger);
    }

    @Test
    void schema_mismatch_reject_update_log_error() {
        // Arrange
        String claimId = "CLM-2023-001";
        Map<String, Object> mismatchedPayload = Map.of("id", claimId, "type", "FNOL", "missing_required", "value");
        String expectedError = "Schema mismatch: missing required field 'policyNumber'";

        // Simulate schema mismatch during validation step
        when(rulesTriage.validatePayload(anyMap())).thenThrow(new IllegalArgumentException(expectedError));

        // Act
        assertDoesNotThrow(() -> orchestrator.processStateTransition(claimId, mismatchedPayload));

        // Assert: Update must be rejected (DynamoDB putItem never invoked)
        verify(claimDataStore, never()).putItem(anyString(), anyMap());

        // Assert: Error must be logged with structured context and exception
        verify(structuredLogger).error(
            eq("Claim Data Standardization: state_transition orchestration failed"),
            any(),
            argThat(throwable -> throwable instanceof IllegalArgumentException && throwable.getMessage().contains("Schema mismatch"))
        );
    }

    /**
     * Minimal orchestrator implementation for test isolation.
     * Simulates the state_transition:orchestration feature flow.
     */
    static class ClaimStateTransitionOrchestrator {
        private final ClaimDataStore claimDataStore;
        private final DocumentManagement documentManagement;
        private final RulesTriage rulesTriage;
        private final Logger logger;

        ClaimStateTransitionOrchestrator(ClaimDataStore claimDataStore, DocumentManagement documentManagement, RulesTriage rulesTriage, Logger logger) {
            this.claimDataStore = claimDataStore;
            this.documentManagement = documentManagement;
            this.rulesTriage = rulesTriage;
            this.logger = logger;
        }

        void processStateTransition(String claimId, Map<String, Object> payload) {
            try {
                // 1. Validate payload against rules schema
                rulesTriage.validatePayload(payload);

                // 2. Persist state transition to Claim Data Store (DynamoDB)
                claimDataStore.putItem(claimId, payload);

                // 3. Archive document to Document Management (S3)
                documentManagement.uploadDocument("Document Management-bucket", "Document Management/" + claimId + ".json", new byte[0]);
            } catch (IllegalArgumentException e) {
                // Reject update on schema mismatch and log structured error
                logger.error("Claim Data Standardization: state_transition orchestration failed", Map.of("claimId", claimId), e);
            }
        }
    }
}
