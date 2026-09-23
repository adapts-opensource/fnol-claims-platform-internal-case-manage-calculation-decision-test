package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Minimal interface definitions for external dependencies to ensure compilation
interface PolicyMatchingService {
    Map<String, Object> resolvePolicy(String policyNumber, String tenantCode);
}

interface ClaimDataStore {
    Map<String, Object> saveStateTransition(String id, Map<String, Object> payload);
}

interface DocumentManagementService {
    String storeDocumentMetadata(String key, String metadata);
}

interface TaskGenerationService {
    List<String> generateTasks(String state, String tenantCode);
}

interface ClaimDataStandardizationOrchestrationService {
    Map<String, Object> executeDecisionFlow(ClaimDataStandardizationStateTransitionOrch stateOrch);
    void logDecision(String id, Map<String, Object> payload);
}

// Typed model per global conventions & data model summary
record ClaimDataStandardizationStateTransitionOrch(String id, Map<String, Object> payload) {}

/**
 * Verifies FNOL routing logic when policy matching fails.
 * Aligns with Claim Data Standardization:orchestration:decision feature.
 */
public class FnolUnmatchedPolicyDecisionRoutingTest {

    private static final Logger logger = LoggerFactory.getLogger(FnolUnmatchedPolicyDecisionRoutingTest.class);

    @Mock
    private PolicyMatchingService policyMatchingService;
    @Mock
    private ClaimDataStore claimDataStore;
    @Mock
    private DocumentManagementService documentManagementService;
    @Mock
    private TaskGenerationService taskGenerationService;
    @Mock
    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void fnol_unmatched_policy_decision_routing() {
        // Arrange inputs per test case specification
        String channel = "Insured Portal";
        String policyNumber = "INVALID-POL";
        String dateOfLoss = "2024-05-01";
        String causeOfLoss = "Water";
        String tenantCode = "FL01";

        // NFR: input_validation
        assertNotNull(channel);
        assertNotNull(policyNumber);
        assertFalse(policyNumber.isBlank());

        Map<String, Object> fnolPayload = Map.of(
                "channel", channel,
                "policy_number", policyNumber,
                "date_of_loss", dateOfLoss,
                "cause_of_loss", causeOfLoss,
                "tenant_code", tenantCode
        );

        String orchestrationId = UUID.randomUUID().toString();
        ClaimDataStandardizationStateTransitionOrch stateOrch = new ClaimDataStandardizationStateTransitionOrch(orchestrationId, fnolPayload);

        // Mock external I/O contracts (DynamoDB, S3, Policy Lookup)
        when(policyMatchingService.resolvePolicy(anyString(), anyString())).thenReturn(null);
        when(claimDataStore.saveStateTransition(anyString(), anyMap())).thenReturn(Map.of("id", orchestrationId, "payload", fnolPayload));
        when(taskGenerationService.generateTasks(anyString(), anyString())).thenReturn(List.of("Resolve Policy Match"));
        when(documentManagementService.storeDocumentMetadata(anyString(), anyString())).thenReturn("s3://Document Management-bucket/Document Management/" + orchestrationId + ".json");

        // Mock structured logging (NFR: observability: structured_logging)
        doNothing().when(orchestrationService).logDecision(anyString(), anyMap());

        // Act
        Map<String, Object> decisionResult = orchestrationService.executeDecisionFlow(stateOrch);

        // Assert expected results
        assertEquals("No Match", decisionResult.get("policy_match_status"));
        assertEquals("Unmatched Policy", decisionResult.get("state"));
        assertEquals(List.of("Resolve Policy Match"), decisionResult.get("tasks"));
        assertNull(decisionResult.get("claim_number"));

        // Verify external calls were made exactly once (thread-safe, no shared state)
        verify(policyMatchingService, times(1)).resolvePolicy(eq(policyNumber), eq(tenantCode));
        verify(claimDataStore, times(1)).saveStateTransition(eq(orchestrationId), anyMap());
        verify(taskGenerationService, times(1)).generateTasks(eq("Unmatched Policy"), eq(tenantCode));
        verify(documentManagementService, times(1)).storeDocumentMetadata(eq(orchestrationId), anyString());
        verify(orchestrationService, times(1)).logDecision(eq(orchestrationId), anyMap());

        logger.info("Test fnol_unmatched_policy_decision_routing completed successfully.");
    }
}
