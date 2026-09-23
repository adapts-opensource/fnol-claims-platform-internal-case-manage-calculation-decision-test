package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.Mockito;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Mock integration tests for Insured Engagement & Tracking: decision: state_transition.
 * Validates input constraints and ensures no downstream calls occur on validation failure.
 */
public class StateTransitionIntegrationMockTest {

    @Mock
    private DecisionStateTransitionService mockTransitionService;

    private InsuredEngagementService insuredEngagementService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        insuredEngagementService = new InsuredEngagementService(mockTransitionService);
    }

    @Test
    @DisplayName("Missing Decision Context")
    void missingDecisionContext() {
        // Arrange
        var request = new StateTransitionRequest();
        request.setClaimId("CLM-001");
        request.setDecisionContext(null); // Simulating missing context

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            insuredEngagementService.transitionState(request);
        }, "System must reject transition request with missing decision context");

        // Verify no calls to downstream mocks since validation should fail fast
        Mockito.verifyNoInteractions(mockTransitionService);
    }
}
