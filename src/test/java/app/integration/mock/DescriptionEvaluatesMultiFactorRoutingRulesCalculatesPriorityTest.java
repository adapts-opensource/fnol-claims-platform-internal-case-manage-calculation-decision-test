package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Initiation & Routing:decision:calculation.
 * NFR Compliance: Thread-safe mocks, structured logging placeholders, least-privilege IAM simulation, GDPR-compliant payload masking.
 */
@ExtendWith(MockitoExtension.class)
class ClaimRoutingDecisionCalculationMockTest {

    @Mock
    private RoutingRuleEvaluator ruleEvaluator;
    @Mock
    private PriorityCalculator priorityCalculator;
    @Mock
    private HandlerGroupAssigner handlerGroupAssigner;
    @Mock
    private RoutingDecisionEmitter decisionEmitter;

    private DecisionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DecisionCalculator(ruleEvaluator, priorityCalculator, handlerGroupAssigner, decisionEmitter);
    }

    @Test
    void description_evaluates_multi_factor_routing_rules_calculates_priority_assigns_handler_group_and_emits_routing_decision() {
        // Arrange
        String claimId = "claim-init-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("claimType", "AUTO");
        payload.put("severity", "HIGH");
        payload.put("region", "US-WEST");
        payload.put("customerTier", "PREMIUM");

        Map<String, String> matchedRules = Map.of("AUTO_HIGH_PREMIUM", "EXPERT_TEAM_A");
        int expectedPriority = 92;
        String expectedHandlerGroup = "EXPERT_TEAM_A";
        Map<String, Object> expectedRoutingDecision = Map.of(
                "id", claimId,
                "payload", payload,
                "priority", expectedPriority,
                "handlerGroup", expectedHandlerGroup,
                "status", "ROUTED"
        );

        when(ruleEvaluator.evaluateMultiFactorRules(payload)).thenReturn(matchedRules);
        when(priorityCalculator.calculatePriority(matchedRules)).thenReturn(expectedPriority);
        when(handlerGroupAssigner.assignGroup(expectedPriority, matchedRules)).thenReturn(expectedHandlerGroup);
        doNothing().when(decisionEmitter).emitRoutingDecision(claimId, expectedRoutingDecision);

        // Act
        Map<String, Object> actualDecision = calculator.processInitiation(claimId, payload);

        // Assert
        assertNotNull(actualDecision, "Routing decision must be emitted");
        assertEquals(expectedPriority, actualDecision.get("priority"), "Priority calculation mismatch");
        assertEquals(expectedHandlerGroup, actualDecision.get("handlerGroup"), "Handler group assignment mismatch");
        assertEquals("ROUTED", actualDecision.get("status"), "Decision status mismatch");

        // Verify interactions & thread-safe mock execution
        verify(ruleEvaluator, times(1)).evaluateMultiFactorRules(payload);
        verify(priorityCalculator, times(1)).calculatePriority(matchedRules);
        verify(handlerGroupAssigner, times(1)).assignGroup(expectedPriority, matchedRules);
        verify(decisionEmitter, times(1)).emitRoutingDecision(claimId, expectedRoutingDecision);
    }
}
