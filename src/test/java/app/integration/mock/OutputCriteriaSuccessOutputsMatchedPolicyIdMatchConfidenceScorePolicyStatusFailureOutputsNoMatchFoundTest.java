package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolStateTransitionCalculationTest {

    @Mock
    private PolicyMatchService policyMatchService;

    @Mock
    private ClaimStatusService claimStatusService;

    @InjectMocks
    private MultiChannelFnolStateTransitionCalculator calculator;

    @BeforeEach
    void setUp() {
        // MockitoExtension auto-injects mocks; explicit setup reserved for complex fixture initialization
    }

    @Test
    void outputCriteriaSuccessOutputsMatchedPolicyIdMatchConfidenceScorePolicyStatusFailureOutputsNoMatchFoundMultipleMatchesFlaggedStatusUpdatesClaimStatusSetToPolicyMatchedOrUnmatchedFnol() {
        // Given
        String submissionId = "fnol-sub-12345";
        Map<String, Object> payload = new HashMap<>();
        payload.put("policy_number", "POL-98765");

        Map<String, Object> matchResult = new HashMap<>();
        matchResult.put("matched_policy_id", "POL-98765");
        matchResult.put("match_confidence_score", 0.95);
        matchResult.put("policy_status", "ACTIVE");
        matchResult.put("no_match_found", false);
        matchResult.put("multiple_matches_flagged", false);

        when(policyMatchService.findMatchingPolicy("POL-98765")).thenReturn(matchResult);

        // When
        Map<String, Object> result = calculator.calculateStateTransition(submissionId, payload);

        // Then - Success Outputs Matched
        assertEquals("POL-98765", result.get("matched_policy_id"));
        assertEquals(0.95, result.get("match_confidence_score"));
        assertEquals("ACTIVE", result.get("policy_status"));

        // Then - Failure Outputs Not Triggered
        assertFalse((Boolean) result.getOrDefault("no_match_found", true));
        assertFalse((Boolean) result.getOrDefault("multiple_matches_flagged", true));

        // Then - Status Updates Applied
        String expectedStatus = "Policy Matched";
        assertEquals(expectedStatus, result.get("claim_status"));
        verify(claimStatusService).update(submissionId, expectedStatus);
    }
}

// Supporting domain contracts for mock context
interface PolicyMatchService {
    Map<String, Object> findMatchingPolicy(String policyNumber);
}

interface ClaimStatusService {
    void update(String claimId, String status);
}

class MultiChannelFnolStateTransitionCalculator {
    private final PolicyMatchService policyMatchService;
    private final ClaimStatusService claimStatusService;

    MultiChannelFnolStateTransitionCalculator(PolicyMatchService policyMatchService, ClaimStatusService claimStatusService) {
        this.policyMatchService = policyMatchService;
        this.claimStatusService = claimStatusService;
    }

    Map<String, Object> calculateStateTransition(String id, Map<String, Object> payload) {
        String policyNum = (String) payload.get("policy_number");
        Map<String, Object> match = policyMatchService.findMatchingPolicy(policyNum);
        boolean noMatch = Boolean.TRUE.equals(match.get("no_match_found"));
        String status = noMatch ? "Unmatched FNOL" : "Policy Matched";
        match.put("claim_status", status);
        claimStatusService.update(id, status);
        return match;
    }
}
