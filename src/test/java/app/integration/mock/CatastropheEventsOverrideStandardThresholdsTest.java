package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionStateTransitionCatastropheTest {

    @Mock
    private ThresholdValidator thresholdValidator;

    @Mock
    private StateTransitionRepository stateTransitionRepository;

    @Mock
    private ComplianceAuditLogger auditLogger;

    private InsuredEngagementService insuredEngagementService;

    @BeforeEach
    void setUp() {
        insuredEngagementService = new InsuredEngagementService(
                thresholdValidator,
                stateTransitionRepository,
                auditLogger
        );
    }

    @Test
    void catastrophe_events_override_standard_thresholds() {
        // Arrange
        String exposureId = "EXP-CAT-999";
        double standardThreshold = 75_000.0;
        boolean isCatastropheEvent = true;
        String expectedTransitionState = "CATASTROPHE_REVIEW_ACTIVE";

        // Standard validation would normally block the transition
        when(thresholdValidator.isWithinThreshold(exposureId, standardThreshold))
                .thenReturn(false);

        // Catastrophe override bypasses validation and forces transition
        when(stateTransitionRepository.updateState(eq(exposureId), anyString(), eq(true)))
                .thenReturn(expectedTransitionState);

        // Act
        String actualState = insuredEngagementService.evaluateAndTransitionState(
                exposureId,
                standardThreshold,
                isCatastropheEvent
        );

        // Assert
        assertEquals(expectedTransitionState, actualState, "State should transition to catastrophe-specific state");
        verify(thresholdValidator, never()).isWithinThreshold(eq(exposureId), anyDouble());
        verify(stateTransitionRepository).updateState(eq(exposureId), anyString(), eq(true));
        verify(auditLogger).logStructuredEvent(eq("STATE_TRANSITION"), anyMap(), eq("CATASTROPHE_OVERRIDE"));
    }
}
