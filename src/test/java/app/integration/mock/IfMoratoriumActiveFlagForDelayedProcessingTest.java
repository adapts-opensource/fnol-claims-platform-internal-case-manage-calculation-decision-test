package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock integration test for Insured Engagement & Tracking state transitions.
 * Validates moratorium-driven delayed processing behavior.
 */
@ExtendWith(MockitoExtension.class)
public class StateTransitionMoratoriumIntegrationMockTest {

    @Mock
    private MoratoriumService moratoriumService;

    @Mock
    private StateTransitionEngine stateTransitionEngine;

    @InjectMocks
    private InsuredEngagementService insuredEngagementService;

    @Test
    void ifMoratoriumActiveFlagForDelayedProcessing() {
        // Given: Moratorium is active for the insured entity
        String entityRef = "CLM-2024-001";
        when(moratoriumService.isActive(entityRef)).thenReturn(true);

        // When: State transition is evaluated
        TransitionContext context = new TransitionContext(entityRef, "INITIATED", "UNDER_REVIEW");
        TransitionResult result = insuredEngagementService.processTransition(context);

        // Then: Transition is flagged for delayed processing
        assertNotNull(result);
        assertEquals(TransitionStatus.DELAYED, result.getStatus());
        assertTrue(result.isFlaggedForDelayedProcessing());
        assertEquals("Moratorium active: processing deferred per compliance policy", result.getReason());

        // Verify moratorium check was invoked and immediate processing was bypassed
        verify(moratoriumService).isActive(entityRef);
        verifyNoInteractions(stateTransitionEngine);
    }
}
