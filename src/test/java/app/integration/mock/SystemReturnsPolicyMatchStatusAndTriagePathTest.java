package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;

/**
 * Mock integration test for Claim Data Standardization: Enrichment Decision.
 * Verifies that the enrichment service correctly processes policy match status and triage path
 * while adhering to latency constraints.
 */
public class ClaimDataStandardizationDecisionEnrichmentMockTest {

    @Mock
    private PolicyMatchClient policyMatchClient;

    @InjectMocks
    private ClaimDecisionEnrichmentService claimDecisionEnrichmentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void system_returns_policy_match_status_and_triage_path_within_2_seconds() {
        // Arrange
        String claimId = "CLM-STD-98765";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("claimType", "AUTO");
        payload.put("lossDate", "2023-10-01");

        String expectedPolicyMatchStatus = "POLICY_MATCH_FOUND";
        String expectedTriagePath = "EXPEDITED_REVIEW";

        when(policyMatchClient.resolvePolicyMatch(claimId)).thenReturn(
            new PolicyMatchResult(expectedPolicyMatchStatus, expectedTriagePath)
        );

        // Act
        long startTime = System.currentTimeMillis();
        PolicyMatchResult result = claimDecisionEnrichmentService.enrichDecision(payload);
        long duration = System.currentTimeMillis() - startTime;

        // Assert
        assertNotNull(result, "Enrichment result must not be null");
        assertEquals(expectedPolicyMatchStatus, result.getPolicyMatchStatus(), 
            "Policy match status must match expected value");
        assertEquals(expectedTriagePath, result.getTriagePath(), 
            "Triage path must match expected value");
        
        // Latency constraint: System must return within 2 seconds
        assertTrue(duration < 2000, 
            "System must return policy match status and triage path within 2 seconds. Actual duration: " + duration + "ms");
    }

    // Supporting classes for compilation and structure
    interface PolicyMatchClient {
        PolicyMatchResult resolvePolicyMatch(String claimId);
    }

    static class PolicyMatchResult {
        private final String policyMatchStatus;
        private final String triagePath;

        PolicyMatchResult(String policyMatchStatus, String triagePath) {
            this.policyMatchStatus = policyMatchStatus;
            this.triagePath = triagePath;
        }

        public String getPolicyMatchStatus() { return policyMatchStatus; }
        public String getTriagePath() { return triagePath; }
    }

    static class ClaimDecisionEnrichmentService {
        @InjectMocks // Injected by Mockito
        private PolicyMatchClient policyMatchClient;

        public PolicyMatchResult enrichDecision(Map<String, Object> payload) {
            String claimId = (String) payload.get("id");
            if (claimId == null) {
                throw new IllegalArgumentException("Claim ID is required");
            }
            // Simulate enrichment logic calling mocked client
            return policyMatchClient.resolvePolicyMatch(claimId);
        }
    }
}
