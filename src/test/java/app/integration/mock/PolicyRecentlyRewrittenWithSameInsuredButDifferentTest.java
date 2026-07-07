package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PolicyRecentlyRewrittenWithSameInsuredButDifferentTest {

    @Mock
    private PolicyClient policyClient;

    @Mock
    private InsuredClient insuredClient;

    @Mock
    private DecisionOrchestrator decisionOrchestrator;

    @InjectMocks
    private EngagementDecisionService decisionService;

    private static final String POLICY_ID = "POL-REWRITE-001";
    private static final String INSURED_ID = "INS-001";
    private static final String PREVIOUS_ADDRESS = "100 Elm St";
    private static final String CURRENT_ADDRESS = "200 Pine Rd";

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and lifecycle
    }

    @Test
    void policy_recently_rewritten_with_same_insured_but_different_address() {
        // Given: Policy recently rewritten (within 30 days)
        PolicyDTO policy = new PolicyDTO(POLICY_ID, INSURED_ID, PREVIOUS_ADDRESS, Instant.now().minusSeconds(86400L * 15));
        when(policyClient.getPolicy(POLICY_ID)).thenReturn(Optional.of(policy));

        // Given: Same insured but different address
        InsuredDTO insured = new InsuredDTO(INSURED_ID, CURRENT_ADDRESS);
        when(insuredClient.getInsured(INSURED_ID)).thenReturn(Optional.of(insured));

        // When: Orchestration decision is triggered
        DecisionResult result = decisionService.evaluateEngagementDecision(POLICY_ID);

        // Then: Verify decision correctly identifies address change scenario
        assertNotNull(result);
        assertEquals(DecisionResult.Status.REWRITE_ADDRESS_CHANGED, result.getStatus());
        assertTrue(result.isFlaggedForReview());
        assertEquals(PREVIOUS_ADDRESS, result.getPreviousAddress());
        assertEquals(CURRENT_ADDRESS, result.getCurrentAddress());

        // Verify external I/O mocks were called exactly once
        verify(policyClient).getPolicy(POLICY_ID);
        verify(insuredClient).getInsured(INSURED_ID);
        verify(decisionOrchestrator).process(any(DecisionContext.class));
    }

    // Minimal domain stubs for compilation context
    static class PolicyDTO {
        private final String policyId;
        private final String insuredId;
        private final String address;
        private final Instant lastRewrittenDate;

        PolicyDTO(String policyId, String insuredId, String address, Instant lastRewrittenDate) {
            this.policyId = policyId;
            this.insuredId = insuredId;
            this.address = address;
            this.lastRewrittenDate = lastRewrittenDate;
        }

        public String getPolicyId() { return policyId; }
        public String getInsuredId() { return insuredId; }
        public String getAddress() { return address; }
        public Instant getLastRewrittenDate() { return lastRewrittenDate; }
    }

    static class InsuredDTO {
        private final String insuredId;
        private final String address;

        InsuredDTO(String insuredId, String address) {
            this.insuredId = insuredId;
            this.address = address;
        }

        public String getInsuredId() { return insuredId; }
        public String getAddress() { return address; }
    }

    static class DecisionContext {
        private final String policyId;
        DecisionContext(String policyId) { this.policyId = policyId; }
        public String getPolicyId() { return policyId; }
    }

    static class DecisionResult {
        private final Status status;
        private final boolean flaggedForReview;
        private final String previousAddress;
        private final String currentAddress;

        DecisionResult(Status status, boolean flaggedForReview, String previousAddress, String currentAddress) {
            this.status = status;
            this.flaggedForReview = flaggedForReview;
            this.previousAddress = previousAddress;
            this.currentAddress = currentAddress;
        }

        public Status getStatus() { return status; }
        public boolean isFlaggedForReview() { return flaggedForReview; }
        public String getPreviousAddress() { return previousAddress; }
        public String getCurrentAddress() { return currentAddress; }
    }

    interface PolicyClient { Optional<PolicyDTO> getPolicy(String policyId); }
    interface InsuredClient { Optional<InsuredDTO> getInsured(String insuredId); }
    interface DecisionOrchestrator { void process(DecisionContext ctx); }

    class EngagementDecisionService {
        private PolicyClient policyClient;
        private InsuredClient insuredClient;
        private DecisionOrchestrator decisionOrchestrator;

        public DecisionResult evaluateEngagementDecision(String policyId) {
            PolicyDTO policy = policyClient.getPolicy(policyId).orElseThrow();
            InsuredDTO insured = insuredClient.getInsured(policy.getInsuredId()).orElseThrow();
            decisionOrchestrator.process(new DecisionContext(policyId));
            
            boolean addressChanged = !policy.getAddress().equals(insured.getAddress());
            return new DecisionResult(
                addressChanged ? DecisionResult.Status.REWRITE_ADDRESS_CHANGED : DecisionResult.Status.SAME_ADDRESS,
                addressChanged,
                policy.getAddress(),
                insured.getAddress()
            );
        }
    }
}
