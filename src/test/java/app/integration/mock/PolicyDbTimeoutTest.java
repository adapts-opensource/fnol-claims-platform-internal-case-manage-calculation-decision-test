package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyDbTimeoutTest {

    @Mock
    private PolicyDbClient policyDbClient;

    @InjectMocks
    private StateTransitionService stateTransitionService;

    @Test
    void policy_db_timeout() {
        String policyId = "POL-98765";
        String targetState = "UNDER_REVIEW";

        // Simulate Policy DB timeout during state transition
        doThrow(new TimeoutException("Database query exceeded maximum execution time"))
                .when(policyDbClient).updatePolicyState(eq(policyId), eq(targetState));

        // Verify that the service wraps the timeout in a domain-specific exception
        PolicyDbTimeoutException thrown = assertThrows(
                PolicyDbTimeoutException.class,
                () -> stateTransitionService.transitionToState(policyId, targetState)
        );

        assertEquals("Database query exceeded maximum execution time", thrown.getMessage());
        verify(policyDbClient, times(1)).updatePolicyState(eq(policyId), eq(targetState));
        verifyNoMoreInteractions(policyDbClient);
    }
}
