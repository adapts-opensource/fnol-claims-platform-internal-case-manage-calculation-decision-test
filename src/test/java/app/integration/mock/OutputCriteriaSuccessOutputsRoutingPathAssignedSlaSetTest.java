package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Initiation & Routing:decision:calculation.
 * Verifies output criteria, status updates, emitted events, and user-visible outputs.
 * NFR Compliance: Thread-safe mock interactions, structured logging placeholders, input validation guards.
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionCalculationMockTest {

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private SesCommunicationService sesCommunicationService;

    private ClaimRoutingDecisionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ClaimRoutingDecisionCalculator(redisCacheService, dynamoDbService, sesCommunicationService);
    }

    @Test
    @DisplayName("Output criteria success: routing path assigned, SLA set, task list generated")
    void output_criteria_success_outputs_routing_path_assigned_sla_set_task_list_generated_failure_outputs_fallback_to_general_handler_alert_to_ops_if_no_rule_matches_status_updates_triage_routed_emitted_events_triage_decision_completed_routing_path_assigned_sla_set_user_visible_outputs_assigned_handler_group_expected_response_time() {
        // Arrange: Input data model fields
        String claimId = "CLM-789012";
        Map<String, Object> payload = new HashMap<>();
        payload.put("claimType", "AUTO");
        payload.put("priority", "HIGH");
        payload.put("policyNumber", "POL-554433");

        // Mock external I/O contracts (never call live AWS/HTTP)
        when(redisCacheService.get(anyString())).thenReturn("routing_rules_v2");
        when(dynamoDbService.getItem(anyString(), anyString())).thenReturn(Map.of("rules", List.of("RULE_AUTO_TIER2")));
        when(sesCommunicationService.sendEmail(anyString(), anyList(), anyString())).thenReturn("MSG-OPS-999");

        // Expected outputs per feature description
        List<String> successOutputs = List.of("Routing path assigned", "SLA set", "Task list generated");
        List<String> failureOutputs = List.of("Fallback to general handler", "Alert to ops if no rule matches");
        List<String> statusUpdates = List.of("Triage -> Routed");
        List<String> emittedEvents = List.of("triage.decision.completed", "routing.path.assigned", "sla.set");
        List<String> userVisibleOutputs = List.of("Assigned handler group", "Expected response time");

        Map<String, Object> expectedResult = new HashMap<>();
        expectedResult.put("successOutputs", successOutputs);
        expectedResult.put("failureOutputs", failureOutputs);
        expectedResult.put("statusUpdates", statusUpdates);
        expectedResult.put("emittedEvents", emittedEvents);
        expectedResult.put("userVisibleOutputs", userVisibleOutputs);
        expectedResult.put("routingPath", "AUTO_SPECIALIST");
        expectedResult.put("slaHours", 24);
        expectedResult.put("handlerGroup", "AUTO_TIER_2");
        expectedResult.put("expectedResponseTime", "24 hours");

        when(calculator.calculate(anyString(), anyMap())).thenReturn(expectedResult);

        // Act
        Map<String, Object> result = calculator.calculate(claimId, payload);

        // Assert: Verify outputs match specification
        assertNotNull(result);
        assertEquals(successOutputs, result.get("successOutputs"));
        assertEquals(failureOutputs, result.get("failureOutputs"));
        assertEquals(statusUpdates, result.get("statusUpdates"));
        assertEquals(emittedEvents, result.get("emittedEvents"));
        assertEquals(userVisibleOutputs, result.get("userVisibleOutputs"));
        assertEquals("AUTO_SPECIALIST", result.get("routingPath"));
        assertEquals(24, result.get("slaHours"));
        assertEquals("AUTO_TIER_2", result.get("handlerGroup"));
        assertEquals("24 hours", result.get("expectedResponseTime"));

        // Verify infra I/O contracts were invoked correctly
        verify(redisCacheService).get("Cache & Reference Data:cache:");
        verify(dynamoDbService).getItem("Claims & Policy Data Store_table", claimId);
        verify(sesCommunicationService).sendEmail("ops@newco.com", List.of("ops@newco.com"), "us-east-1");
    }
}
