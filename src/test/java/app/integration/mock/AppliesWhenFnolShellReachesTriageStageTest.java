package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private DocumentManagementClient documentManagementClient;

    private ClaimDataStandardizationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimDataStandardizationOrchestrator(claimDataStoreClient, documentManagementClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void appliesWhenFnolShellReachesTriageStage() {
        // Arrange: Simulate FNOL shell reaching triage stage
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "id", claimId,
                "stage", "TRIAGE",
                "fnolData", Map.of("type", "AUTO_CLAIM", "severity", "HIGH")
        );

        // Act: Trigger orchestration for state transition
        orchestrator.processStateTransition(payload);

        // Assert: Verify DynamoDB write for claim data standardization state
        ArgumentCaptor<Map<String, Object>> itemCaptor = ArgumentCaptor.forClass(Map.class);
        verify(claimDataStoreClient, times(1))
                .putItem(eq("Claim Data Store_table"), eq("pk"), itemCaptor.capture());

        Map<String, Object> savedItem = itemCaptor.getValue();
        assertEquals(claimId, savedItem.get("id"));
        assertEquals("TRIAGE", savedItem.get("stage"));

        // Assert: Verify S3 document write with correct key pattern
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(documentManagementClient, times(1))
                .putObject(eq("Document Management-bucket"), keyCaptor.capture(), any());

        String expectedKey = "Document Management/" + claimId + ".json";
        assertEquals(expectedKey, keyCaptor.getValue());
    }

    // Minimal dependency interfaces for mocking external I/O
    interface ClaimDataStoreClient {
        void putItem(String tableName, String partitionKey, Map<String, Object> payload);
    }

    interface DocumentManagementClient {
        void putObject(String bucketName, String objectKey, Object content);
    }

    // Orchestration service under test
    static class ClaimDataStandardizationOrchestrator {
        private final ClaimDataStoreClient claimDataStoreClient;
        private final DocumentManagementClient documentManagementClient;

        ClaimDataStandardizationOrchestrator(ClaimDataStoreClient claimDataStoreClient, DocumentManagementClient documentManagementClient) {
            this.claimDataStoreClient = claimDataStoreClient;
            this.documentManagementClient = documentManagementClient;
        }

        void processStateTransition(Map<String, Object> payload) {
            String id = (String) payload.get("id");
            String stage = (String) payload.get("stage");

            // Standardize and persist state transition to DynamoDB
            Map<String, Object> standardizedItem = Map.of("id", id, "stage", stage, "payload", payload);
            claimDataStoreClient.putItem("Claim Data Store_table", "pk", standardizedItem);

            // Persist standardized document to S3
            String objectKey = "Document Management/" + id + ".json";
            documentManagementClient.putObject("Document Management-bucket", objectKey, payload);
        }
    }
}
