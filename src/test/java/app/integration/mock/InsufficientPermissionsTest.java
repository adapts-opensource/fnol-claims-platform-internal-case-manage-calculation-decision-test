package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementStateTransitionTest {

    @Mock
    private PermissionValidator permissionValidator;

    @Mock
    private StateTransitionEngine stateTransitionEngine;

    @InjectMocks
    private InsuredEngagementService insuredEngagementService;

    @Test
    void insufficientPermissions() {
        String claimId = "CLM-9981";
        String targetState = "APPROVED";
        String userRole = "DATA_ENTRY_CLERK";

        when(permissionValidator.hasAuthorization(claimId, userRole, "STATE_TRANSITION"))
                .thenThrow(new SecurityException("Insufficient permissions"));

        assertThrows(SecurityException.class, () -> {
            insuredEngagementService.transitionState(claimId, targetState, userRole);
        });

        verify(permissionValidator, times(1)).hasAuthorization(claimId, userRole, "STATE_TRANSITION");
        verify(stateTransitionEngine, never()).execute(anyString(), anyString());
    }
}
