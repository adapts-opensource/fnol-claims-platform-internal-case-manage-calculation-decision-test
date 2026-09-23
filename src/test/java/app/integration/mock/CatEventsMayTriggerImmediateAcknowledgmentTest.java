package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Claim Initiation & Routing Decision Calculation")
class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private DecisionCalculationService decisionCalculationService;

    @Mock
    private AcknowledgmentPublisher acknowledgmentPublisher;

    @InjectMocks
    private ClaimInitiationRouter claimInitiationRouter;

    @Test
    @DisplayName("CAT events may trigger immediate acknowledgment")
    void cat_events_may_trigger_immediate_acknowledgment() {
        // Arrange
        String claimId = "CAT-CLM-2024-001";
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "eventType", "CAT",
            "isCatastropheEvent", true,
            "severity", "HIGH",
            "routingStrategy", "IMMEDIATE_ACK"
        );

        // Mock the decision calculation to return an immediate routing decision
        when(decisionCalculationService.calculateDecision(payload))
                .thenReturn(RoutingDecision.IMMEDIATE_ACKNOWLEDGMENT);

        // Act
        claimInitiationRouter.initiateAndRoute(payload);

        // Assert
        verify(decisionCalculationService, times(1)).calculateDecision(payload);
        verify(acknowledgmentPublisher, times(1))
                .publishImmediateAcknowledgment(eq(claimId), any(Map.class));
        // Ensure standard/delayed acknowledgment path is not triggered for CAT events
        verify(acknowledgmentPublisher, never())
                .publishDelayedAcknowledgment(eq(claimId), any(Map.class));
    }
}
