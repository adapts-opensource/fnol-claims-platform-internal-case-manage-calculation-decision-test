package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private ClaimOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimOrchestrationService(claimDataStoreClient, documentManagementClient);
    }

    @Test
    void handler_assigned_based_on_routing_logic() {
        // Given
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "claimType", "AUTO",
                "severity", "HIGH",
                "routingStrategy", "SENIOR_ADJUSTER_POOL"
        );

        // When
        String assignedHandler = orchestrationService.assignHandlerBasedOnRouting(claimId, payload);

        // Then
        assertEquals("SENIOR_ADJUSTER_POOL", assignedHandler);

        // Verify infra I/O contracts were invoked correctly per NFR compliance
        verify(claimDataStoreClient).putItem(
                eq("Claim Data Store_table"),
                eq("pk"),
                argThat(item -> item.containsKey("id") && item.containsKey("payload"))
        );
        verify(documentManagementClient).writeObject(
                eq("Document Management-bucket"),
                eq("Document Management/" + claimId + ".json"),
                any()
        );
    }

    // Mock interfaces for external I/O (DynamoDB & S3) to satisfy contract validation
    interface ClaimDataStoreClient {
        void putItem(String tableName, String partitionKey, Map<String, Object> item);
    }

    interface DocumentManagementClient {
        void writeObject(String bucketName, String objectKey, Object content);
    }

    // Service under test simulating Claim Data Standardization:state_transition:orchestration
    static class ClaimOrchestrationService {
        private final ClaimDataStoreClient claimDataStoreClient;
        private final DocumentManagementClient documentManagementClient;

        ClaimOrchestrationService(ClaimDataStoreClient claimDataStoreClient, DocumentManagementClient documentManagementClient) {
            this.claimDataStoreClient = claimDataStoreClient;
            this.documentManagementClient = documentManagementClient;
        }

        String assignHandlerBasedOnRouting(String claimId, Map<String, Object> payload) {
            // Extract routing rule from standardized payload
            String routingRule = (String) payload.getOrDefault("routingStrategy", "DEFAULT_HANDLER");

            // Construct standardized state transition entity per data model
            Map<String, Object> standardizedItem = Map.of(
                    "id", claimId,
                    "payload", payload
            );

            // Persist to mocked infrastructure (simulates DynamoDB & S3 writes)
            claimDataStoreClient.putItem("Claim Data Store_table", "pk", standardizedItem);
            documentManagementClient.writeObject("Document Management-bucket", "Document Management/" + claimId + ".json", payload);

            return routingRule;
        }
    }
}
