package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionAuthorizationStatusRuleTest {

    @Mock
    private ClaimAuthorizationService authorizationService;

    private final ClaimRoutingOrchestrator orchestrator = new ClaimRoutingOrchestrator();

    @Test
    void decision_authorization_status_rule_if_valid_proceed_if_invalid_expired_reject_with_reason_expected_outcome_access_granted_or_denied_with_clear_reason() {
        // Arrange & Act & Assert for VALID -> Proceed/Grant
        when(authorizationService.checkStatus("claim-001")).thenReturn(new AuthorizationResult(AuthorizationStatus.VALID, null));
        AuthorizationResult validResult = orchestrator.evaluate("claim-001", authorizationService);
        assertTrue(validResult.isGranted(), "Expected access granted for valid authorization");
        assertNull(validResult.getReason(), "Expected no rejection reason for valid authorization");

        // Arrange & Act & Assert for INVALID -> Reject/Deny with reason
        when(authorizationService.checkStatus("claim-002")).thenReturn(new AuthorizationResult(AuthorizationStatus.INVALID, "Policy number not found"));
        AuthorizationResult invalidResult = orchestrator.evaluate("claim-002", authorizationService);
        assertFalse(invalidResult.isGranted(), "Expected access denied for invalid authorization");
        assertEquals("Policy number not found", invalidResult.getReason(), "Expected clear reason for invalid authorization");

        // Arrange & Act & Assert for EXPIRED -> Reject/Deny with reason
        when(authorizationService.checkStatus("claim-003")).thenReturn(new AuthorizationResult(AuthorizationStatus.EXPIRED, "Coverage period ended"));
        AuthorizationResult expiredResult = orchestrator.evaluate("claim-003", authorizationService);
        assertFalse(expiredResult.isGranted(), "Expected access denied for expired authorization");
        assertEquals("Coverage period ended", expiredResult.getReason(), "Expected clear reason for expired authorization");

        verify(authorizationService, times(3)).checkStatus(anyString());
    }

    // Supporting domain and service classes for test isolation
    enum AuthorizationStatus { VALID, INVALID, EXPIRED, DENIED }

    static class AuthorizationResult {
        private final AuthorizationStatus status;
        private final String reason;

        AuthorizationResult(AuthorizationStatus status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        public AuthorizationStatus getStatus() { return status; }
        public String getReason() { return reason; }
        public boolean isGranted() { return status == AuthorizationStatus.VALID; }
    }

    static class ClaimAuthorizationService {
        public AuthorizationResult checkStatus(String claimId) { return null; }
    }

    static class ClaimRoutingOrchestrator {
        public AuthorizationResult evaluate(String claimId, ClaimAuthorizationService authService) {
            AuthorizationResult result = authService.checkStatus(claimId);
            if (result.isGranted()) {
                return new AuthorizationResult(AuthorizationStatus.VALID, null);
            }
            return new AuthorizationResult(AuthorizationStatus.DENIED, result.getReason());
        }
    }
}
