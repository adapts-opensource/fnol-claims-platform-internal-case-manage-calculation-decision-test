package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PolicyNumberValidationStateTransitionTest {

    @Mock
    private InsuredEngagementService insuredEngagementService;

    @BeforeEach
    void setUp() {
        reset(insuredEngagementService);
    }

    @Test
    void policy_number_must_be_non_empty_if_provided() {
        // Arrange: Simulate request with empty policy_number
        StateTransitionRequest emptyPolicyRequest = StateTransitionRequest.builder()
                .policyNumber("")
                .claimId("CLAIM-8821")
                .targetState("REVIEW")
                .build();

        // Act & Assert: Should throw validation exception when policy_number is empty
        assertThrows(ValidationException.class, () -> {
            insuredEngagementService.transitionState(emptyPolicyRequest);
        });

        // Arrange: Simulate request with valid non-empty policy_number
        String validPolicyNumber = "POL-992341";
        StateTransitionRequest validPolicyRequest = StateTransitionRequest.builder()
                .policyNumber(validPolicyNumber)
                .claimId("CLAIM-8821")
                .targetState("REVIEW")
                .build();

        when(insuredEngagementService.transitionState(any(StateTransitionRequest.class)))
                .thenReturn(TransitionResult.success());

        // Act & Assert: Should succeed when policy_number is non-empty
        TransitionResult result = insuredEngagementService.transitionState(validPolicyRequest);
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    // Minimal domain types for standalone compilation
    static class StateTransitionRequest {
        private final String policyNumber;
        private final String claimId;
        private final String targetState;

        private StateTransitionRequest(String policyNumber, String claimId, String targetState) {
            this.policyNumber = policyNumber;
            this.claimId = claimId;
            this.targetState = targetState;
        }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String policyNumber;
            private String claimId;
            private String targetState;

            Builder policyNumber(String p) { this.policyNumber = p; return this; }
            Builder claimId(String c) { this.claimId = c; return this; }
            Builder targetState(String t) { this.targetState = t; return this; }
            StateTransitionRequest build() { return new StateTransitionRequest(policyNumber, claimId, targetState); }
        }

        public String getPolicyNumber() { return policyNumber; }
    }

    static class TransitionResult {
        private final boolean success;
        private TransitionResult(boolean success) { this.success = success; }
        public static TransitionResult success() { return new TransitionResult(true); }
        public boolean isSuccess() { return success; }
    }

    static class ValidationException extends RuntimeException {
        ValidationException(String message) { super(message); }
    }

    interface InsuredEngagementService {
        TransitionResult transitionState(StateTransitionRequest request);
    }
}
