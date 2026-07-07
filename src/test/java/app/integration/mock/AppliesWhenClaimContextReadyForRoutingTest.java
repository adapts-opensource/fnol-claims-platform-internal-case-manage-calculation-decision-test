package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppliesWhenClaimContextReadyForRoutingTest {

    @Mock
    private RoutingDecisionCalculator routingDecisionCalculator;

    private ClaimContextProcessor claimContextProcessor;

    @BeforeEach
    void setUp() {
        claimContextProcessor = new ClaimContextProcessor(routingDecisionCalculator);
    }

    @Test
    void applies_when_claim_context_ready_for_routing() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
            "status", "READY_FOR_ROUTING",
            "priority", "HIGH",
            "type", "AUTO_CLAIM"
        );
        ClaimContext context = new ClaimContext(claimId, payload);

        RoutingDecision expectedDecision = new RoutingDecision("ROUTING_APPLIED", "Triage_Queue_A");
        when(routingDecisionCalculator.calculate(context)).thenReturn(expectedDecision);

        // Act
        RoutingDecision actualDecision = claimContextProcessor.processIfReady(context);

        // Assert
        assertNotNull(actualDecision);
        assertEquals(expectedDecision.status(), actualDecision.status());
        assertEquals(expectedDecision.route(), actualDecision.route());
        verify(routingDecisionCalculator, times(1)).calculate(context);
    }

    // Internal interfaces and models to isolate the calculation logic
    interface RoutingDecisionCalculator {
        RoutingDecision calculate(ClaimContext context);
    }

    record ClaimContext(String id, Map<String, Object> payload) {}

    record RoutingDecision(String status, String route) {}

    static class ClaimContextProcessor {
        private final RoutingDecisionCalculator calculator;

        ClaimContextProcessor(RoutingDecisionCalculator calculator) {
            this.calculator = calculator;
        }

        RoutingDecision processIfReady(ClaimContext context) {
            String status = (String) context.payload().get("status");
            if ("READY_FOR_ROUTING".equals(status)) {
                return calculator.calculate(context);
            }
            throw new IllegalStateException("Claim context not ready for routing");
        }
    }
}
