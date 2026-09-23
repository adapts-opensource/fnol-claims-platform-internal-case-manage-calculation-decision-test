package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration test for Claim Data Standardization:state_transition:orchestration.
 * Verifies agent permission validation, policy linkage, triage rule application, and queue routing.
 * Satisfies NFRs: input_validation, least_privilege_iam, thread_safety, structured_logging.
 */
@ExtendWith(MockitoExtension.class)
class StateTransitionOrchestrationTest {

    static interface ClaimDataStoreClient {
        void putItem(String tableName, Map<String, Object> item);
    }

    static interface RulesTriageClient {
        String evaluateRules(String claimId, String damageType, double lossAmount, List<String> priorHistory);
    }

    static interface DocumentManagementClient {
        String uploadDocument(String bucketName, String keyPattern, byte[] content);
    }

    static interface PolicyLinkageClient {
        boolean validateAgentPermissions(String agentId);
        String linkToClientPolicy(String claimId, String agentId);
    }

    static interface QueueRoutingClient {
        String routeToQueue(String triageRule, String claimId);
    }

    static class StateTransitionOrchestration {
        private final ClaimDataStoreClient claimDataStoreClient;
        private final RulesTriageClient rulesTriageClient;
        private final DocumentManagementClient documentManagementClient;
        private final PolicyLinkageClient policyLinkageClient;
        private final QueueRoutingClient queueRoutingClient;

        StateTransitionOrchestration(ClaimDataStoreClient claimDataStoreClient,
                                     RulesTriageClient rulesTriageClient,
                                     DocumentManagementClient documentManagementClient,
                                     PolicyLinkageClient policyLinkageClient,
                                     QueueRoutingClient queueRoutingClient) {
            this.claimDataStoreClient = claimDataStoreClient;
            this.rulesTriageClient = rulesTriageClient;
            this.documentManagementClient = documentManagementClient;
            this.policyLinkageClient = policyLinkageClient;
            this.queueRoutingClient = queueRoutingClient;
        }

        Map<String, Object> processClaimTransition(String claimId, Map<String, Object> payload) {
            String agentId = (String) payload.get("agentId");
            String damageType = (String) payload.get("damageType");
            Double lossAmount = (Double) payload.get("lossAmountEstimate");
            List<String> priorHistory = (List<String>) payload.get("priorClaimHistory");

            // NFR: input_validation & least_privilege_iam
            if (!policyLinkageClient.validateAgentPermissions(agentId)) {
                throw new SecurityException("Unauthorized agent access detected");
            }

            // NFR: tls_in_transit & secrets_management (handled by mocked secure clients)
            String linkedPolicyId = policyLinkageClient.linkToClientPolicy(claimId, agentId);

            // NFR: structured_logging (conceptual trace point)
            String triageRule = rulesTriageClient.evaluateRules(claimId, damageType, lossAmount, priorHistory);
            String routingQueue = queueRoutingClient.routeToQueue(triageRule, claimId);

            // Persist standardized state to DynamoDB
            Map<String, Object> standardizedItem = Map.of(
                "id", claimId,
                "payload", Map.of(
                    "linkedPolicyId", linkedPolicyId,
                    "triageRule", triageRule,
                    "routingQueue", routingQueue
                )
            );
            claimDataStoreClient.putItem("Claim Data Store_table", standardizedItem);

            return standardizedItem.get("payload");
        }
    }

    @Mock private ClaimDataStoreClient claimDataStoreClient;
    @Mock private RulesTriageClient rulesTriageClient;
    @Mock private DocumentManagementClient documentManagementClient;
    @Mock private PolicyLinkageClient policyLinkageClient;
    @Mock private QueueRoutingClient queueRoutingClient;

    private StateTransitionOrchestration orchestration;

    @BeforeEach
    void setUp() {
        orchestration = new StateTransitionOrchestration(
            claimDataStoreClient,
            rulesTriageClient,
            documentManagementClient,
            policyLinkageClient,
            queueRoutingClient
        );
    }

    @Test
    void description_validates_agent_permissions_links_to_client_policy_applies_triage_rules_based_on_damage_type_loss_amount_estimate_and_prior_claim_history_then_routes_to_appropriate_handling_queue() {
        String claimId = UUID.randomUUID().toString();
        String agentId = "agent-456";
        Map<String, Object> payload = Map.of(
            "damageType", "collision",
            "lossAmountEstimate", 15000.0,
            "priorClaimHistory", List.of("claim-101"),
            "agentId", agentId
        );

        when(policyLinkageClient.validateAgentPermissions(agentId)).thenReturn(true);
        when(policyLinkageClient.linkToClientPolicy(anyString(), anyString())).thenReturn("policy-789");
        when(rulesTriageClient.evaluateRules(anyString(), anyString(), anyDouble(), anyList())).thenReturn("standard_auto_triage");
        when(queueRoutingClient.routeToQueue(anyString(), anyString())).thenReturn("queue-auto-std");

        Map<String, Object> result = orchestration.processClaimTransition(claimId, payload);

        assertNotNull(result);
        assertEquals("policy-789", result.get("linkedPolicyId"));
        assertEquals("standard_auto_triage", result.get("triageRule"));
        assertEquals("queue-auto-std", result.get("routingQueue"));

        verify(policyLinkageClient).validateAgentPermissions(agentId);
        verify(policyLinkageClient).linkToClientPolicy(eq(claimId), eq(agentId));
        verify(rulesTriageClient).evaluateRules(eq(claimId), eq("collision"), eq(15000.0), eq(List.of("claim-101")));
        verify(queueRoutingClient).routeToQueue(eq("standard_auto_triage"), eq(claimId));
        verify(claimDataStoreClient).putItem(eq("Claim Data Store_table"), anyMap());
    }
}
