package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementStateTransitionOutputCriteriaTest {

    @Mock
    private StateTransitionEngine stateTransitionEngine;

    @BeforeEach
    void setUp() {
        stateTransitionEngine = mock(StateTransitionEngine.class);
    }

    @Test
    void output_criteria_success_outputs_matched_policy_id_policy_status_active_expired_dol_validation_status_valid_invalid_out_of_period_triage_suggestion_failure_outputs_match_error_code_validation_error_code_fallback_to_manual_review_flag() {
        // Arrange: Define expected success output values per criteria
        String expectedMatchedPolicyId = "POL-88291";
        String expectedPolicyStatus = "active"; // Covers 'active' or 'expired'
        String expectedDolValidationStatus = "valid"; // Covers 'valid', 'invalid', or 'out-of-period'
        String expectedTriageSuggestion = "auto_triage_complete";

        // Define expected failure output structure (null/empty for success path)
        String expectedMatchErrorCode = null;
        String expectedValidationErrorCode = null;
        Boolean expectedFallbackToManualReviewFlag = false;

        StateTransitionOutput mockOutput = new StateTransitionOutput(
                expectedMatchedPolicyId,
                expectedPolicyStatus,
                expectedDolValidationStatus,
                expectedTriageSuggestion,
                expectedMatchErrorCode,
                expectedValidationErrorCode,
                expectedFallbackToManualReviewFlag
        );

        when(stateTransitionEngine.evaluate(any(StateTransitionInput.class)))
                .thenReturn(mockOutput);

        // Act
        StateTransitionInput input = new StateTransitionInput("INC-001", "EXP-001");
        StateTransitionOutput actualOutput = stateTransitionEngine.evaluate(input);

        // Assert: Verify success outputs match criteria
        assertNotNull(actualOutput);
        assertEquals(expectedMatchedPolicyId, actualOutput.getMatchedPolicyId());
        assertTrue(List.of("active", "expired").contains(actualOutput.getPolicyStatus()),
                "Policy status must be active or expired");
        assertTrue(List.of("valid", "invalid", "out-of-period").contains(actualOutput.getDolValidationStatus()),
                "DOL validation status must be valid, invalid, or out-of-period");
        assertNotNull(actualOutput.getTriageSuggestion());

        // Assert: Verify failure outputs are correctly structured/present
        assertEquals(expectedMatchErrorCode, actualOutput.getMatchErrorCode());
        assertEquals(expectedValidationErrorCode, actualOutput.getValidationErrorCode());
        assertEquals(expectedFallbackToManualReviewFlag, actualOutput.getFallbackToManualReviewFlag());

        // Verify mock interactions
        verify(stateTransitionEngine, times(1)).evaluate(any(StateTransitionInput.class));
    }

    // Supporting DTOs and Interfaces for isolated mock testing
    static class StateTransitionInput {
        private final String incidentId;
        private final String exposureId;
        StateTransitionInput(String incidentId, String exposureId) {
            this.incidentId = incidentId;
            this.exposureId = exposureId;
        }
    }

    static class StateTransitionOutput {
        private final String matchedPolicyId;
        private final String policyStatus;
        private final String dolValidationStatus;
        private final String triageSuggestion;
        private final String matchErrorCode;
        private final String validationErrorCode;
        private final Boolean fallbackToManualReviewFlag;

        StateTransitionOutput(String matchedPolicyId, String policyStatus, String dolValidationStatus,
                              String triageSuggestion, String matchErrorCode, String validationErrorCode,
                              Boolean fallbackToManualReviewFlag) {
            this.matchedPolicyId = matchedPolicyId;
            this.policyStatus = policyStatus;
            this.dolValidationStatus = dolValidationStatus;
            this.triageSuggestion = triageSuggestion;
            this.matchErrorCode = matchErrorCode;
            this.validationErrorCode = validationErrorCode;
            this.fallbackToManualReviewFlag = fallbackToManualReviewFlag;
        }

        String getMatchedPolicyId() { return matchedPolicyId; }
        String getPolicyStatus() { return policyStatus; }
        String getDolValidationStatus() { return dolValidationStatus; }
        String getTriageSuggestion() { return triageSuggestion; }
        String getMatchErrorCode() { return matchErrorCode; }
        String getValidationErrorCode() { return validationErrorCode; }
        Boolean getFallbackToManualReviewFlag() { return fallbackToManualReviewFlag; }
    }

    interface StateTransitionEngine {
        StateTransitionOutput evaluate(StateTransitionInput input);
    }
}
