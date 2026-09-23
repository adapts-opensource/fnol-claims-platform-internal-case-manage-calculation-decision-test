package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FallbackRoutingForEdgeCasesTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private DocumentManagementClient documentManagementClient;

    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimDataStandardizationOrchestrationService(
                claimDataStoreClient, documentManagementClient);
    }

    @Test
    void fallback_routing_for_edge_cases() {
        // Arrange: Simulate edge cases where primary routing fails or receives invalid/partial input
        String testId = "edge-case-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> edgeCasePayload = Map.of(
                "claimId", "CLM-999",
                "status", "UNKNOWN",
                "amount", null // Edge case: missing/null value to trigger validation/fallback
        );

        // Primary DynamoDB route fails (simulating timeout/connection issue or validation rejection)
        when(claimDataStoreClient.saveItem(anyString(), anyMap()))
                .thenThrow(new RuntimeException("DynamoDB connection timeout"));

        // Fallback S3 route succeeds
        when(documentManagementClient.saveDocument(anyString(), anyString()))
                .thenReturn("s3://document-management-bucket/claims/" + testId + ".json");

        // Act: Execute orchestration
        OrchestrationResult result = orchestrationService.routeAndPersist(testId, edgeCasePayload);

        // Assert: Verify fallback was triggered and data was preserved safely
        assertNotNull(result, "Result should not be null after fallback");
        assertEquals(testId, result.getId(), "ID should match input");
        assertTrue(result.isFallbackUsed(), "Fallback routing should be marked as true");
        assertFalse(result.hasPiiExposure(), "PII exposure should be false per compliance NFR");

        // Verify primary was attempted exactly once before fallback
        verify(claimDataStoreClient, times(1)).saveItem(anyString(), anyMap());
        // Verify fallback was invoked once
        verify(documentManagementClient, times(1)).saveDocument(anyString(), anyString());
    }

    // Minimal internal interfaces to keep test self-contained and mockable
    interface ClaimDataStoreClient {
        void saveItem(String pk, Map<String, Object> payload) throws RuntimeException;
    }

    interface DocumentManagementClient {
        String saveDocument(String bucket, String key) throws RuntimeException;
    }

    interface OrchestrationResult {
        String getId();
        boolean isFallbackUsed();
        boolean hasPiiExposure();
    }

    // SUT demonstrating fallback routing logic aligned with NFRs
    static class ClaimDataStandardizationOrchestrationService {
        private final ClaimDataStoreClient dataStore;
        private final DocumentManagementClient docStore;

        ClaimDataStandardizationOrchestrationService(ClaimDataStoreClient dataStore, DocumentManagementClient docStore) {
            this.dataStore = dataStore;
            this.docStore = docStore;
        }

        OrchestrationResult routeAndPersist(String id, Map<String, Object> payload) {
            boolean fallbackUsed = false;
            boolean piiExposure = false;

            try {
                // Input validation (Security NFR)
                if (payload == null || payload.isEmpty()) {
                    throw new IllegalArgumentException("Payload cannot be null or empty");
                }

                // Attempt primary route (DynamoDB)
                dataStore.saveItem("pk", payload);
            } catch (Exception e) {
                // Fallback routing for edge cases
                fallbackUsed = true;
                try {
                    // Secure fallback path with least-privilege pattern simulation
                    docStore.saveDocument("Document Management-bucket", id + ".json");
                } catch (Exception fallbackEx) {
                    // Log structured error & flag compliance risk
                    piiExposure = true;
                }
            }

            return new OrchestrationResult() {
                @Override
                public String getId() { return id; }
                @Override
                public boolean isFallbackUsed() { return fallbackUsed; }
                @Override
                public boolean hasPiiExposure() { return piiExposure; }
            };
        }
    }
}
