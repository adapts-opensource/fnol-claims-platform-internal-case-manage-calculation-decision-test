package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// Simplified infrastructure contracts for mocking DynamoDB and S3
interface ClaimDataStore {
    void putItem(String tableName, String partitionKey, Map<String, Object> item);
}

interface DocumentStore {
    void putObject(String bucketName, String objectKey, String body);
}

// Orchestration logic under test
class StateTransitionOrchestrator {
    private final ClaimDataStore claimDataStore;
    private final DocumentStore documentStore;

    public StateTransitionOrchestrator(ClaimDataStore claimDataStore, DocumentStore documentStore) {
        this.claimDataStore = claimDataStore;
        this.documentStore = documentStore;
    }

    public void handleTimeoutRevert(String id, Map<String, Object> payload) {
        String currentState = (String) payload.get("state");
        Instant timeoutAt = (Instant) payload.get("approval_timeout");
        Instant now = Instant.now();

        if ("APPROVAL_PENDING".equals(currentState) && timeoutAt != null && timeoutAt.isBefore(now)) {
            payload.put("state", "DRAFT");
            payload.put("transition_reason", "APPROVAL_TIMEOUT");
            claimDataStore.putItem("Claim Data Store_table", "pk", payload);
            documentStore.putObject("Document Management-bucket", "Document Management/" + id + ".json", payload.toString());
        }
    }
}

class ClaimDataStandardizationStateTransitionOrchMockTest {
    @Mock
    private ClaimDataStore mockClaimDataStore;
    @Mock
    private DocumentStore mockDocumentStore;

    private StateTransitionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new StateTransitionOrchestrator(mockClaimDataStore, mockDocumentStore);
    }

    @Test
    void approval_timeout_revert_to_draft() {
        String claimId = "claim-123";
        Map<String, Object> payload = new HashMap<>();
        payload.put("state", "APPROVAL_PENDING");
        payload.put("approval_timeout", Instant.now().minusSeconds(1800));
        payload.put("claim_number", "CLM-2024-001");

        orchestrator.handleTimeoutRevert(claimId, payload);

        assertEquals("DRAFT", payload.get("state"));
        assertEquals("APPROVAL_TIMEOUT", payload.get("transition_reason"));
        verify(mockClaimDataStore, times(1)).putItem(eq("Claim Data Store_table"), eq("pk"), payload);
        verify(mockDocumentStore, times(1)).putObject(eq("Document Management-bucket"), eq("Document Management/" + claimId + ".json"), anyString());
    }
}
