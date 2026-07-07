package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationStateTransitionOrchMockTest {

    static interface ClaimDataStoreClient {
        Map<String, Object> getItem(String claimId);
    }

    static interface DocumentManagementClient {
        String storeDocument(String bucketName, String objectKey);
    }

    static interface RulesAndTriageClient {
        boolean applyRules(String claimId, Map<String, Object> payload);
    }

    static class ClaimDataStandardizationStateTransitionOrchestrator {
        private final ClaimDataStoreClient claimDataStoreClient;
        private final DocumentManagementClient documentManagementClient;
        private final RulesAndTriageClient rulesAndTriageClient;

        public ClaimDataStandardizationStateTransitionOrchestrator(
                ClaimDataStoreClient claimDataStoreClient,
                DocumentManagementClient documentManagementClient,
                RulesAndTriageClient rulesAndTriageClient) {
            this.claimDataStoreClient = claimDataStoreClient;
            this.documentManagementClient = documentManagementClient;
            this.rulesAndTriageClient = rulesAndTriageClient;
        }

        public Map<String, Object> processStateTransition(String claimId, Map<String, Object> payload) {
            Map<String, Object> statePayload = claimDataStoreClient.getItem(claimId);
            boolean rulesApplied = rulesAndTriageClient.applyRules(claimId, payload);
            String docUri = documentManagementClient.storeDocument("Document Management-bucket", "claim/" + claimId + ".json");

            Map<String, Object> result = new HashMap<>(statePayload);
            result.put("documentUri", docUri);
            result.put("rulesApplied", rulesApplied);
            return result;
        }
    }

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private DocumentManagementClient documentManagementClient;

    @Mock
    private RulesAndTriageClient rulesAndTriageClient;

    @InjectMocks
    private ClaimDataStandardizationStateTransitionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test execution
    }

    @Test
    void policy_recently_rewritten_with_new_effective_date() {
        // Arrange
        String claimId = "CLM-TEST-001";
        String policyId = "POL-REWRITE-99";
        LocalDate newEffectiveDate = LocalDate.of(2024, 11, 1);
        LocalDateTime rewriteTimestamp = LocalDateTime.now().minusDays(3);

        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("policyId", policyId);
        inputPayload.put("recentlyRewritten", true);
        inputPayload.put("effectiveDate", newEffectiveDate.toString());
        inputPayload.put("rewriteTimestamp", rewriteTimestamp.toString());

        Map<String, Object> expectedStatePayload = new HashMap<>(inputPayload);
        expectedStatePayload.put("standardizationState", "READY_FOR_VALIDATION");
        expectedStatePayload.put("effectiveDateUpdated", true);

        when(claimDataStoreClient.getItem(claimId)).thenReturn(expectedStatePayload);
        when(rulesAndTriageClient.applyRules(claimId, inputPayload)).thenReturn(true);
        when(documentManagementClient.storeDocument(anyString(), anyString())).thenReturn("s3://bucket/claim/" + claimId + ".json");

        // Act
        Map<String, Object> result = orchestrator.processStateTransition(claimId, inputPayload);

        // Assert
        assertNotNull(result);
        assertEquals("READY_FOR_VALIDATION", result.get("standardizationState"));
        assertTrue((Boolean) result.get("effectiveDateUpdated"));
        assertEquals("s3://bucket/claim/" + claimId + ".json", result.get("documentUri"));
        assertTrue((Boolean) result.get("rulesApplied"));

        verify(claimDataStoreClient).getItem(claimId);
        verify(rulesAndTriageClient).applyRules(claimId, inputPayload);
        verify(documentManagementClient).storeDocument(eq("Document Management-bucket"), eq("claim/" + claimId + ".json"));
    }
}
