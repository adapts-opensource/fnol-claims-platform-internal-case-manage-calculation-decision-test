package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PolicyLapsedButReinstatedTest {

    @Mock
    private PolicyStateTransitionEngine stateTransitionEngine;

    @Mock
    private DynamoDbPersistenceLayer persistenceLayer;

    @Mock
    private SesCommunicationLayer communicationLayer;

    private InsuredEngagementService insuredEngagementService;

    @BeforeEach
    void setUp() {
        insuredEngagementService = new InsuredEngagementService(stateTransitionEngine, persistenceLayer, communicationLayer);
    }

    @Test
    void policy_lapsed_but_reinstated() {
        // Arrange
        String policyId = "POL-LAPSED-001";
        String previousState = "LAPSED";
        String nextState = "REINSTATED";

        when(stateTransitionEngine.validateTransition(policyId, previousState, nextState)).thenReturn(true);
        when(persistenceLayer.updatePolicyState(policyId, nextState)).thenReturn(true);
        when(communicationLayer.sendReinstatementNotification(policyId)).thenReturn("SES-MSG-98765");

        // Act
        TransitionOutcome outcome = insuredEngagementService.executeTransition(policyId, previousState, nextState);

        // Assert
        assertNotNull(outcome, "Transition outcome should not be null");
        assertEquals(TransitionStatus.SUCCESS, outcome.status(), "State transition should succeed");
        assertEquals("SES-MSG-98765", outcome.notificationMessageId(), "Reinstatement notification should be triggered");

        verify(stateTransitionEngine).validateTransition(policyId, previousState, nextState);
        verify(persistenceLayer).updatePolicyState(policyId, nextState);
        verify(communicationLayer).sendReinstatementNotification(policyId);
    }
}
