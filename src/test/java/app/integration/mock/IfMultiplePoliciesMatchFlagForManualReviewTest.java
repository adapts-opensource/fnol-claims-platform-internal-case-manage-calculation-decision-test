package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateTransitionMockTest {

    @Mock
    private PolicyMatcherService policyMatcherService;

    @Mock
    private EngagementTrackingService engagementTrackingService;

    @InjectMocks
    private DecisionStateTransitionService stateTransitionService;

    @BeforeEach
    void setUp() {
        // Reset mocks and initialize test context
    }

    @Test
    void ifMultiplePoliciesMatchFlagForManualReview() {
        // Arrange
        String claimId = "CLM-12345";
        List<String> matchedPolicyIds = List.of("POL-001", "POL-002");
        
        // Mock policy matching to return multiple hits (simulates external data lookup)
        when(policyMatcherService.findMatchingPolicies(claimId)).thenReturn(matchedPolicyIds);

        // Act
        String newState = stateTransitionService.transitionState(claimId, "INITIATED");

        // Assert
        assertEquals("FLAGGED_FOR_MANUAL_REVIEW", newState, "State should transition to manual review when multiple policies match");
        verify(engagementTrackingService).flagForManualReview(claimId, matchedPolicyIds);
        verifyNoMoreInteractions(policyMatcherService, engagementTrackingService);
    }
}
