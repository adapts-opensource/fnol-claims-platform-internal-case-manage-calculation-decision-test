package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyRecentlyCanceledReinstatedTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private DocumentManagementClient documentManagementClient;

    @InjectMocks
    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @Test
    void policy_recently_canceled_reinstated() {
        // Arrange
        String claimId = "CLM-12345";
        Map<String, Object> payload = new HashMap<>();
        payload.put("policyId", "POL-98765");
        payload.put("policyStatus", "CANCELLED_REINSTATED");
        payload.put("cancellationDate", "2023-10-01");
        payload.put("reinstatementDate", "2023-10-05");

        Map<String, Object> expectedTransformedState = new HashMap<>();
        expectedTransformedState.put("id", claimId);
        expectedTransformedState.put("payload", payload);
        expectedTransformedState.put("standardizedPolicyStatus", "ACTIVE");
        expectedTransformedState.put("riskFlag", "RECENT_CANCELLATION");
        expectedTransformedState.put("requiresManualReview", true);

        when(claimDataStoreClient.saveStateTransition(anyString(), anyMap()))
                .thenReturn(expectedTransformedState);
        when(documentManagementClient.storeDocument(anyString(), anyString()))
                .thenReturn("s3://doc-bucket/CLM-12345.json");

        // Act
        Map<String, Object> result = orchestrationService.processStateTransition(claimId, payload);

        // Assert
        assertNotNull(result);
        assertEquals("ACTIVE", result.get("standardizedPolicyStatus"));
        assertEquals("RECENT_CANCELLATION", result.get("riskFlag"));
        assertTrue((Boolean) result.get("requiresManualReview"));
        verify(claimDataStoreClient).saveStateTransition(eq(claimId), eq(payload));
        verify(documentManagementClient).storeDocument(eq(claimId), anyString());
    }

    // Minimal client interfaces to satisfy mock compilation without external SDKs
    interface ClaimDataStoreClient {
        Map<String, Object> saveStateTransition(String id, Map<String, Object> payload);
    }

    interface DocumentManagementClient {
        String storeDocument(String claimId, String objectKey);
    }

    // Service implementation under test
    static class ClaimDataStandardizationOrchestrationService {
        private final ClaimDataStoreClient claimDataStoreClient;
        private final DocumentManagementClient documentManagementClient;

        ClaimDataStandardizationOrchestrationService(
                ClaimDataStoreClient claimDataStoreClient,
                DocumentManagementClient documentManagementClient) {
            this.claimDataStoreClient = claimDataStoreClient;
            this.documentManagementClient = documentManagementClient;
        }

        Map<String, Object> processStateTransition(String claimId, Map<String, Object> payload) {
            Map<String, Object> transformedState = new HashMap<>(payload);
            transformedState.put("id", claimId);

            // Orchestration logic for recently canceled/reinstated policy
            if ("CANCELLED_REINSTATED".equals(payload.get("policyStatus"))) {
                transformedState.put("standardizedPolicyStatus", "ACTIVE");
                transformedState.put("riskFlag", "RECENT_CANCELLATION");
                transformedState.put("requiresManualReview", true);
            }

            // Persist state to DynamoDB equivalent
            claimDataStoreClient.saveStateTransition(claimId, transformedState);
            // Store document to S3 equivalent
            documentManagementClient.storeDocument(claimId, claimId + ".json");

            return transformedState;
        }
    }
}
