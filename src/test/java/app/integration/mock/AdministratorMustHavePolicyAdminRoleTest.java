package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdministratorMustHavePolicyAdminRoleTest {

    @Mock
    private RoleAuthorizationService roleAuthorizationService;

    @Mock
    private StateTransitionExecutor stateTransitionExecutor;

    private InsuredEngagementDecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        decisionEngine = new InsuredEngagementDecisionEngine(roleAuthorizationService, stateTransitionExecutor);
    }

    @Test
    void administrator_must_have_policy_admin_role() {
        // Given: Administrator lacks the required POLICY_ADMIN role
        String administratorId = "admin-7f8a9b";
        String currentState = "PENDING_REVIEW";
        String targetState = "APPROVED";
        when(roleAuthorizationService.hasRole(administratorId, "POLICY_ADMIN")).thenReturn(false);

        // When & Then: Transition should be blocked and throw AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> {
            decisionEngine.evaluateStateTransition(administratorId, currentState, targetState);
        });

        // Verify that no state change was propagated to downstream systems
        verify(stateTransitionExecutor, never()).applyTransition(anyString(), anyString(), anyString());
    }

    @Test
    void administrator_with_policy_admin_role_allows_transition() {
        // Given: Administrator possesses the POLICY_ADMIN role
        String administratorId = "admin-7f8a9b";
        String currentState = "PENDING_REVIEW";
        String targetState = "APPROVED";
        when(roleAuthorizationService.hasRole(administratorId, "POLICY_ADMIN")).thenReturn(true);
        doNothing().when(stateTransitionExecutor).applyTransition(anyString(), anyString(), anyString());

        // When & Then: Transition should proceed without throwing
        assertDoesNotThrow(() -> {
            decisionEngine.evaluateStateTransition(administratorId, currentState, targetState);
        });

        // Verify transition was dispatched exactly once
        verify(stateTransitionExecutor).applyTransition(administratorId, currentState, targetState);
    }

    // Minimal domain interfaces to ensure standalone compilation
    static class AccessDeniedException extends RuntimeException {
        AccessDeniedException(String message) {
            super(message);
        }
    }

    interface RoleAuthorizationService {
        boolean hasRole(String administratorId, String requiredRole);
    }

    interface StateTransitionExecutor {
        void applyTransition(String administratorId, String currentState, String targetState);
    }

    static class InsuredEngagementDecisionEngine {
        private final RoleAuthorizationService roleAuthorizationService;
        private final StateTransitionExecutor stateTransitionExecutor;

        InsuredEngagementDecisionEngine(RoleAuthorizationService roleAuthorizationService,
                                        StateTransitionExecutor stateTransitionExecutor) {
            this.roleAuthorizationService = roleAuthorizationService;
            this.stateTransitionExecutor = stateTransitionExecutor;
        }

        void evaluateStateTransition(String administratorId, String currentState, String targetState) {
            if (!roleAuthorizationService.hasRole(administratorId, "POLICY_ADMIN")) {
                throw new AccessDeniedException("Administrator must have POLICY_ADMIN role to transition state.");
            }
            stateTransitionExecutor.applyTransition(administratorId, currentState, targetState);
        }
    }
}
