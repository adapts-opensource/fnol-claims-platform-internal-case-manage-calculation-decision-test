package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditTrailCapturesDecisionContextTest {

    @Mock
    private DecisionCalculationService mockCalculationService;

    @Mock
    private AuditTrailService mockAuditTrailService;

    private ClaimRoutingDecisionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ClaimRoutingDecisionCalculator(mockCalculationService, mockAuditTrailService);
    }

    @Test
    void audit_trail_captures_decision_context() {
        // Arrange
        String claimId = "CLM-" + UUID.randomUUID();
        Map<String, Object> claimPayload = Map.of("claimType", "AUTO", "severity", "HIGH");
        Map<String, Object> expectedContext = Map.of(
            "decisionId", "DEC-" + UUID.randomUUID(),
            "routingRule", "SENIOR_ADJUSTER",
            "confidenceScore", 0.92,
            "auditTimestamp", System.currentTimeMillis()
        );

        when(mockCalculationService.calculate(anyMap())).thenReturn(expectedContext);

        // Act
        calculator.initiateAndRouteClaim(claimId, claimPayload);

        // Assert
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockAuditTrailService, times(1)).captureContext(eq(claimId), contextCaptor.capture());

        Map<String, Object> capturedContext = contextCaptor.getValue();
        assertNotNull(capturedContext, "Audit trail must capture a non-null context");
        assertEquals(expectedContext.get("routingRule"), capturedContext.get("routingRule"));
        assertEquals(expectedContext.get("confidenceScore"), capturedContext.get("confidenceScore"));
        assertTrue(capturedContext.containsKey("auditTimestamp"));
        verify(mockCalculationService, times(1)).calculate(anyMap());
    }

    private static interface DecisionCalculationService {
        Map<String, Object> calculate(Map<String, Object> payload);
    }

    private static interface AuditTrailService {
        void captureContext(String claimId, Map<String, Object> context);
    }

    private static class ClaimRoutingDecisionCalculator {
        private final DecisionCalculationService calculationService;
        private final AuditTrailService auditTrailService;

        ClaimRoutingDecisionCalculator(DecisionCalculationService calculationService, AuditTrailService auditTrailService) {
            this.calculationService = calculationService;
            this.auditTrailService = auditTrailService;
        }

        void initiateAndRouteClaim(String claimId, Map<String, Object> payload) {
            Map<String, Object> decision = calculationService.calculate(payload);
            auditTrailService.captureContext(claimId, decision);
        }
    }
}
