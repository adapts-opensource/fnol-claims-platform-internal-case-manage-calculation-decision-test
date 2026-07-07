package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AgentReportsForPolicyNotInTheirBookTest {

    @Mock
    private PolicyOwnershipValidator policyOwnershipValidator;

    @Mock
    private ClaimDataStore claimDataStore;

    private StateTransitionOrchestrator stateTransitionOrchestrator;

    private static final String POLICY_ID = "POL-789012";
    private static final String AGENT_ID = "AGT-345678";
    private static final String CLAIM_ID = "CLM-112233";

    @BeforeEach
    void setUp() {
        stateTransitionOrchestrator = new StateTransitionOrchestrator(policyOwnershipValidator, claimDataStore);
    }

    @Test
    void agent_reports_for_policy_not_in_their_book() {
        // Arrange: Construct claim data standardization payload
        Map<String, Object> claimPayload = Map.of(
                "id", CLAIM_ID,
                "policyId", POLICY_ID,
                "reportingAgentId", AGENT_ID,
                "state", "NEW"
        );

        // Mock external policy lookup to simulate policy not in agent's book
        when(policyOwnershipValidator.verifyAgentPolicyOwnership(POLICY_ID, AGENT_ID))
                .thenReturn(false);

        // Act & Assert: Verify orchestration rejects the claim with expected error
        Exception thrown = assertThrows(IllegalStateException.class, () -> {
            stateTransitionOrchestrator.processStandardization(claimPayload);
        });

        // Verify: External I/O interactions and state transitions
        verify(policyOwnershipValidator, times(1)).verifyAgentPolicyOwnership(POLICY_ID, AGENT_ID);
        verify(claimDataStore, never()).putItem(anyString(), anyMap());
        assertNotNull(thrown);
        assertTrue(thrown.getMessage().contains("POLICY_NOT_IN_AGENT_BOOK"));
    }

    // Mock dependencies representing external I/O contracts
    private interface PolicyOwnershipValidator {
        boolean verifyAgentPolicyOwnership(String policyId, String agentId);
    }

    private interface ClaimDataStore {
        void putItem(String tableName, Map<String, Object> item);
    }

    // System Under Test: Orchestration layer
    private static class StateTransitionOrchestrator {
        private final PolicyOwnershipValidator validator;
        private final ClaimDataStore store;

        StateTransitionOrchestrator(PolicyOwnershipValidator validator, ClaimDataStore store) {
            this.validator = validator;
            this.store = store;
        }

        void processStandardization(Map<String, Object> payload) {
            String policyId = (String) payload.get("policyId");
            String agentId = (String) payload.get("reportingAgentId");

            // Input validation & ownership check
            if (!validator.verifyAgentPolicyOwnership(policyId, agentId)) {
                throw new IllegalStateException("POLICY_NOT_IN_AGENT_BOOK");
            }

            // Standardization & persistence (mocked)
            Map<String, Object> standardItem = Map.of(
                    "id", payload.get("id"),
                    "standardized", true,
                    "state", "VALIDATED"
            );
            store.putItem("Claim Data Store_table", standardItem);
        }
    }
}
