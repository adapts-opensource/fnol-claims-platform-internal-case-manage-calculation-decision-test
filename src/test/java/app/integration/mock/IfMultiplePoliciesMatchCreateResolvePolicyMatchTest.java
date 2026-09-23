package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IfMultiplePoliciesMatchCreateResolvePolicyMatchTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private TaskQueueService taskQueueService;

    private ClaimDecisionService claimDecisionService;

    @BeforeEach
    void setUp() {
        claimDecisionService = new ClaimDecisionService(
                policyValidationService,
                documentStoreService,
                rulesEngineService,
                taskQueueService
        );
    }

    @Test
    @DisplayName("if_multiple_policies_match_create_resolve_policy_match_task")
    void if_multiple_policies_match_create_resolve_policy_match_task() {
        // Arrange
        String claimId = "claim-789";
        Map<String, Object> payload = Map.of(
                "claimId", claimId,
                "policySearchCriteria", Map.of("type", "AUTO", "state", "CA")
        );
        List<String> matchingPolicies = List.of("POL-001", "POL-002", "POL-003");

        when(policyValidationService.findMatchingPolicies(eq(claimId), anyMap())).thenReturn(matchingPolicies);
        when(documentStoreService.storeClaimData(anyString(), anyString(), anyMap())).thenReturn("s3://fnol-bucket/claims/claim-789.json");

        // Act
        claimDecisionService.processClaimStandardization(claimId, payload);

        // Assert
        ArgumentCaptor<Map<String, Object>> taskPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(taskQueueService).enqueueTask(eq("RESOLVE_POLICY_MATCH"), eq(claimId), taskPayloadCaptor.capture());

        Map<String, Object> capturedPayload = taskPayloadCaptor.getValue();
        assertNotNull(capturedPayload);
        assertTrue(capturedPayload.containsKey("matchedPolicyIds"));
        @SuppressWarnings("unchecked")
        List<String> capturedPolicies = (List<String>) capturedPayload.get("matchedPolicyIds");
        assertEquals(3, capturedPolicies.size());
        assertEquals(matchingPolicies, capturedPolicies);

        verify(policyValidationService).findMatchingPolicies(eq(claimId), anyMap());
        verify(documentStoreService).storeClaimData(eq("fnol-bucket"), eq("claims/claim-789.json"), anyMap());
        verifyNoMoreInteractions(policyValidationService, documentStoreService, rulesEngineService, taskQueueService);
    }

    // Minimal service interfaces representing infra I/O contracts
    private interface PolicyValidationService {
        List<String> findMatchingPolicies(String claimId, Map<String, Object> payload);
    }

    private interface DocumentStoreService {
        String storeClaimData(String bucketName, String objectKeyPattern, Map<String, Object> data);
    }

    private interface RulesEngineService {
        Map<String, Object> evaluateRules(String claimId, Map<String, Object> payload);
    }

    private interface TaskQueueService {
        void enqueueTask(String taskType, String entityId, Map<String, Object> taskPayload);
    }

    // Service under test
    private static class ClaimDecisionService {
        private final PolicyValidationService policyValidationService;
        private final DocumentStoreService documentStoreService;
        private final RulesEngineService rulesEngineService;
        private final TaskQueueService taskQueueService;

        ClaimDecisionService(PolicyValidationService policyValidationService, DocumentStoreService documentStoreService, RulesEngineService rulesEngineService, TaskQueueService taskQueueService) {
            this.policyValidationService = policyValidationService;
            this.documentStoreService = documentStoreService;
            this.rulesEngineService = rulesEngineService;
            this.taskQueueService = taskQueueService;
        }

        void processClaimStandardization(String claimId, Map<String, Object> payload) {
            // Persist claim data to S3
            documentStoreService.storeClaimData("fnol-bucket", "claims/" + claimId + ".json", payload);

            // Validate and search for matching policies
            List<String> matchingPolicies = policyValidationService.findMatchingPolicies(claimId, payload);

            // Decision: If multiple policies match, create a Resolve Policy Match task
            if (matchingPolicies != null && matchingPolicies.size() > 1) {
                Map<String, Object> taskPayload = Map.of(
                        "matchedPolicyIds", matchingPolicies,
                        "reason", "MULTIPLE_POLICIES_MATCH",
                        "claimId", claimId
                );
                taskQueueService.enqueueTask("RESOLVE_POLICY_MATCH", claimId, taskPayload);
            }
        }
    }
}
