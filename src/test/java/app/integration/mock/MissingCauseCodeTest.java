package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

public class ClaimDataStandardizationTransformationOrchestrationMockTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private RulesTriageClient rulesTriageClient;

    private ClaimTransformationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new ClaimTransformationOrchestrator(claimDataStoreClient, rulesTriageClient);
    }

    @Test
    void missing_cause_code() {
        // Arrange: Payload mimicking claim_data_standardization_state_transition_orch structure
        Map<String, Object> payload = new HashMap<>();
        payload.put("claim_id", "CLM-998877");
        payload.put("incident_date", "2023-11-15");
        // cause_code is intentionally omitted to simulate missing data

        String recordId = "ORCH-554433";

        // Act & Assert: Verify orchestration fails fast on missing required field
        ValidationException thrown = assertThrows(
                ValidationException.class,
                () -> orchestrator.transformAndOrchestrate(recordId, payload),
                "Orchestration should reject payload when cause_code is missing"
        );

        // Verify external I/O clients are never invoked due to early validation
        verifyNoInteractions(claimDataStoreClient, rulesTriageClient);
        assertTrue(thrown.getMessage().contains("cause_code"), "Error message should reference the missing field");
    }

    // --- Infrastructure Client Interfaces (Mocked) ---
    interface ClaimDataStoreClient {
        void saveItem(String tableName, String pk, Map<String, Object> itemPayload);
    }

    interface RulesTriageClient {
        void updateRules(String tableName, String pk, Map<String, Object> itemPayload);
    }

    // --- Domain Exceptions ---
    static class ValidationException extends RuntimeException {
        ValidationException(String message) {
            super(message);
        }
    }

    // --- Service Under Test ---
    static class ClaimTransformationOrchestrator {
        private final ClaimDataStoreClient claimDataStoreClient;
        private final RulesTriageClient rulesTriageClient;

        ClaimTransformationOrchestrator(ClaimDataStoreClient claimDataStoreClient, RulesTriageClient rulesTriageClient) {
            this.claimDataStoreClient = claimDataStoreClient;
            this.rulesTriageClient = rulesTriageClient;
        }

        void transformAndOrchestrate(String id, Map<String, Object> payload) {
            // Input validation per NFR: input_validation & compliance
            if (payload == null || !payload.containsKey("cause_code") || payload.get("cause_code") == null) {
                throw new ValidationException("Validation failed: missing required field 'cause_code' in claim payload");
            }

            // Mocked external I/O contracts (DynamoDB & Rules & Triage)
            claimDataStoreClient.saveItem("Claim Data Store_table", "pk", payload);
            rulesTriageClient.updateRules("Rules & Triage Service_table", "pk", payload);
        }
    }
}
