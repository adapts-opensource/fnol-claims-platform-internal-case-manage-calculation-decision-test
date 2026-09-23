package app.integration.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Insured Engagement & Tracking:decision:state_transition - PolicyDbDown")
public class PolicyDbDownTest {

    @Mock
    private PolicyDatabaseClient policyDbClient;

    @InjectMocks
    private StateTransitionService stateTransitionService;

    @Test
    @DisplayName("policy_db_down")
    void policy_db_down() {
        // Arrange: Simulate Policy DB connectivity failure
        when(policyDbClient.getConnection())
                .thenThrow(new RuntimeException("Connection refused: Policy DB is down"));

        // Act & Assert: Verify state transition fails gracefully with a controlled exception
        assertThrows(RuntimeException.class, () -> {
            stateTransitionService.transitionState("claim-001", "UNDER_REVIEW");
        });

        // Verify: Ensure DB access was attempted exactly once before failure propagation
        verify(policyDbClient, times(1)).getConnection();
        verifyNoMoreInteractions(policyDbClient);
    }
}
