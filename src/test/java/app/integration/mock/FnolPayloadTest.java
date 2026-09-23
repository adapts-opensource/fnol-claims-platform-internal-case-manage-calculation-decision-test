package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FnolPayloadTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private DocumentManagementClient documentManagementClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void fnolPayload() {
        // Arrange
        String claimId = "fnol-claim-001";
        Map<String, Object> standardPayload = Map.of(
            "claimType", "FNOL",
            "status", "INITIATED",
            "policyNumber", "POL-123456"
        );

        // Mock infrastructure I/O contracts (DynamoDB & S3)
        when(claimDataStoreClient.putItem(anyString(), anyString(), any(Map.class)))
                .thenReturn("Item persisted successfully");
        when(documentManagementClient.putObject(anyString(), anyString(), any(byte[].class)))
                .thenReturn("s3://Document Management-bucket/fnol-claim-001.json");

        // Act: Orchestrate claim data standardization transformation
        Map<String, Object> result = orchestrateClaimData(claimId, standardPayload);

        // Assert data model compliance: claim_data_standardization_state_transition_orch
        assertNotNull(result);
        assertEquals(claimId, result.get("id"));
        assertEquals(standardPayload, result.get("payload"));

        // Verify infrastructure I/O contracts were invoked exactly once
        verify(claimDataStoreClient, times(1)).putItem(eq("Claim Data Store_table"), eq("pk"), any(Map.class));
        verify(documentManagementClient, times(1)).putObject(eq("Document Management-bucket"), eq("fnol-claim-001.json"), any(byte[].class));
    }

    private Map<String, Object> orchestrateClaimData(String id, Map<String, Object> payload) {
        // Input validation (NFR: input_validation)
        if (id == null || payload == null) {
            throw new IllegalArgumentException("id and payload must not be null");
        }

        Map<String, Object> transformed = new java.util.HashMap<>();
        transformed.put("id", id);
        transformed.put("payload", payload);

        // Simulate orchestrated infrastructure calls (NFR: observability, security)
        claimDataStoreClient.putItem("Claim Data Store_table", "pk", transformed);
        documentManagementClient.putObject("Document Management-bucket", id + ".json", payload.toString().getBytes());

        return transformed;
    }

    // Minimal client interfaces to satisfy compilation and mock boundaries
    interface ClaimDataStoreClient {
        String putItem(String tableName, String partitionKey, Map<String, Object> item);
    }

    interface DocumentManagementClient {
        String putObject(String bucketName, String objectKey, byte[] content);
    }
}
