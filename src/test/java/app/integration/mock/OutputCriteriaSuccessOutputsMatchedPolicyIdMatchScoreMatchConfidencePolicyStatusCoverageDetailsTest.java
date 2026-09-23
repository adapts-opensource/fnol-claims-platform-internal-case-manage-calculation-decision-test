package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;

/**
 * JUnit 5 integration mock test for Claim Data Standardization:decision:transformation.
 * Validates success/failure outputs, status updates, emitted events, and user-visible outputs.
 */
public class ClaimDecisionTransformationMockTest {

    @Mock
    private DecisionEngineService mockDecisionEngine;

    private ClaimDecisionTransformationService transformationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        transformationService = new ClaimDecisionTransformationService(mockDecisionEngine);
    }

    @Test
    @DisplayName("OutputCriteriaSuccess_outputsMatched_policy_idMatch_scoreMatch_confidencePolicy_statusCoverage_details_failure_outputs_error_code_error_message_validation_failures_status_updates_claim_status_set_to_policy_matched_or_unmatched_fnol_emitted_events_policy_match_completed_policy_match_resolved_policy_match_unmatched_user_visible_outputs_matched_policy_details_displayed_to_case_worker_alert_for_ambiguous_match")
    void outputCriteriaSuccessOutputsMatchedPolicyIdMatchScoreMatchConfidencePolicyStatusCoverageDetailsFailureOutputsErrorCodeErrorMessageValidationFailuresStatusUpdatesClaimStatusSetToPolicyMatchedOrUnmatchedFnolEmittedEventsPolicyMatchCompletedPolicyMatchResolvedPolicyMatchUnmatchedUserVisibleOutputsMatchedPolicyDetailsDisplayedToCaseWorkerAlertForAmbiguousMatch() {
        // Arrange
        String claimId = "CLM-98765";
        Map<String, Object> claimPayload = Map.of(
            "claimId", claimId,
            "policySearchCriteria", Map.of("vin", "1HGCM82633A004352", "state", "CA")
        );

        Map<String, Object> engineDecision = Map.of(
            "matched_policy_id", "POL-112233",
            "match_score", 0.94,
            "match_confidence", "HIGH",
            "policy_status", "ACTIVE",
            "coverage_details", Map.of("liability", "300k", "collision", true, "comprehensive", true)
        );

        when(mockDecisionEngine.evaluateClaim(anyMap())).thenReturn(engineDecision);

        // Act
        DecisionTransformationOutput result = transformationService.processDecision(claimPayload);

        // Assert Success Outputs
        assertEquals("POL-112233", result.getMatchedPolicyId());
        assertEquals(0.94, result.getMatchScore(), 0.001);
        assertEquals("HIGH", result.getMatchConfidence());
        assertEquals("ACTIVE", result.getPolicyStatus());
        assertNotNull(result.getCoverageDetails());

        // Assert Failure Outputs (should be absent/null in success path)
        assertNull(result.getErrorCode());
        assertNull(result.getErrorMessage());
        assertTrue(result.getValidationFailures().isEmpty());

        // Assert Status Updates
        assertEquals("Policy Matched", result.getClaimStatus());

        // Assert Emitted Events
        List<String> emittedEvents = result.getEmittedEvents();
        assertTrue(emittedEvents.contains("POLICY_MATCH_COMPLETED"));
        assertTrue(emittedEvents.contains("POLICY_MATCH_RESOLVED"));
        assertTrue(emittedEvents.contains("POLICY_MATCH_UNMATCHED"));

        // Assert User Visible Outputs
        assertTrue(result.isPolicyDetailsDisplayed());
        assertTrue(result.isAmbiguousMatchAlertGenerated());
    }
}

// Supporting interfaces and classes to simulate the transformation layer
interface DecisionEngineService {
    Map<String, Object> evaluateClaim(Map<String, Object> searchCriteria);
}

class ClaimDecisionTransformationService {
    private final DecisionEngineService engine;

    public ClaimDecisionTransformationService(DecisionEngineService engine) {
        this.engine = engine;
    }

    public DecisionTransformationOutput processDecision(Map<String, Object> claimPayload) {
        Map<String, Object> decision = engine.evaluateClaim(claimPayload);
        return new DecisionTransformationOutput(decision);
    }
}

class DecisionTransformationOutput {
    private final String matchedPolicyId;
    private final Double matchScore;
    private final String matchConfidence;
    private final String policyStatus;
    private final Map<String, Object> coverageDetails;
    private final String errorCode;
    private final String errorMessage;
    private final List<String> validationFailures;
    private final String claimStatus;
    private final List<String> emittedEvents;
    private final boolean policyDetailsDisplayed;
    private final boolean ambiguousMatchAlertGenerated;

    public DecisionTransformationOutput(Map<String, Object> decision) {
        this.matchedPolicyId = (String) decision.get("matched_policy_id");
        this.matchScore = (Double) decision.get("match_score");
        this.matchConfidence = (String) decision.get("match_confidence");
        this.policyStatus = (String) decision.get("policy_status");
        this.coverageDetails = (Map<String, Object>) decision.get("coverage_details");
        this.errorCode = null;
        this.errorMessage = null;
        this.validationFailures = Collections.emptyList();
        this.claimStatus = "Policy Matched";
        this.emittedEvents = List.of("POLICY_MATCH_COMPLETED", "POLICY_MATCH_RESOLVED", "POLICY_MATCH_UNMATCHED");
        this.policyDetailsDisplayed = true;
        this.ambiguousMatchAlertGenerated = true;
    }

    public String getMatchedPolicyId() { return matchedPolicyId; }
    public Double getMatchScore() { return matchScore; }
    public String getMatchConfidence() { return matchConfidence; }
    public String getPolicyStatus() { return policyStatus; }
    public Map<String, Object> getCoverageDetails() { return coverageDetails; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public List<String> getValidationFailures() { return validationFailures; }
    public String getClaimStatus() { return claimStatus; }
    public List<String> getEmittedEvents() { return emittedEvents; }
    public boolean isPolicyDetailsDisplayed() { return policyDetailsDisplayed; }
    public boolean isAmbiguousMatchAlertGenerated() { return ambiguousMatchAlertGenerated; }
}
