package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppliesWhenPreClaimValidationPhaseTest {

    @Mock
    private StateTransitionDecisionEngine decisionEngine;

    @Mock
    private ReserveLineRepository reserveLineRepository;

    @InjectMocks
    private InsuredEngagementTrackingService trackingService;

    private String claimId;
    private String currentPhase;

    @BeforeEach
    void setUp() {
        claimId = "CLM-8821";
        currentPhase = "PRE_CLAIM_VALIDATION";
    }

    @Test
    void applies_when_pre_claim_validation_phase() {
        // Arrange
        String expectedNextState = "VALIDATION_COMPLETE";
        when(decisionEngine.evaluatePhaseEligibility(currentPhase)).thenReturn(true);
        when(decisionEngine.transitionState(claimId, currentPhase)).thenReturn(expectedNextState);
        when(reserveLineRepository.findByClaimId(claimId)).thenReturn(null);

        // Act
        String actualState = trackingService.processStateTransition(claimId, currentPhase);

        // Assert
        assertEquals(expectedNextState, actualState);
        verify(decisionEngine).evaluatePhaseEligibility(currentPhase);
        verify(decisionEngine).transitionState(claimId, currentPhase);
        verify(reserveLineRepository).findByClaimId(claimId);
    }
}
