package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingOrchestrationTransformationMockTest {

    @Mock
    private PolicyMatchingService policyMatchingService;
    @Mock
    private RoutingService routingService;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private AuditLogger auditLogger;
    @Mock
    private S3Client s3Client;
    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private ClaimOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // Mocks are initialized by MockitoExtension
        when(s3Client.putObject(anyString(), anyString(), any())).thenReturn(null);
        when(dynamoDbClient.putItem(anyString(), any())).thenReturn(null);
    }

    @Test
    void output_criteria_success_outputs_matched_policy_id_match_score_coverage_context_routing_path() {
        Map<String, Object> input = new HashMap<>();
        input.put("policyNumber", "POL-123456");
        input.put("fnolId", "FNOL-789");

        Map<String, Object> matchResult = new HashMap<>();
        matchResult.put("matched_policy_id", "POL-123456");
        matchResult.put("match_score", 95);
        matchResult.put("coverage_context", Map.of("auto", "collision", "liability", "full"));
        matchResult.put("routing_path", "AUTO_STANDARD_ROUTING");

        when(policyMatchingService.findPolicy(anyString())).thenReturn(matchResult);
        when(routingService.determinePath(anyMap())).thenReturn("AUTO_STANDARD_ROUTING");

        Map<String, Object> result = orchestrationService.transformAndRoute(input);

        assertEquals("POL-123456", result.get("matched_policy_id"));
        assertEquals(95, result.get("match_score"));
        assertNotNull(result.get("coverage_context"));
        assertEquals("AUTO_STANDARD_ROUTING", result.get("routing_path"));
        verify(eventPublisher).publish(eq("FNOL.PolicyMatch.Completed"), any());
    }

    @Test
    void failure_outputs_no_match_status_multiple_match_flag_review_task_created() {
        Map<String, Object> input = new HashMap<>();
        input.put("policyNumber", "POL-INVALID");

        Map<String, Object> failureResult = new HashMap<>();
        failureResult.put("no_match_status", "EXACT_MATCH_FAILED");
        failureResult.put("multiple_match_flag", false);
        failureResult.put("review_task_created", true);

        when(policyMatchingService.findPolicy(anyString())).thenReturn(failureResult);

        Map<String, Object> result = orchestrationService.transformAndRoute(input);

        assertEquals("EXACT_MATCH_FAILED", result.get("no_match_status"));
        assertFalse((Boolean) result.get("multiple_match_flag"));
        assertTrue((Boolean) result.get("review_task_created"));
        verify(eventPublisher).publish(eq("FNOL.PolicyMatch.ReviewRequired"), any());
    }

    @Test
    void status_updates_fnol_status_policy_matched_unmatched_multiple_match() {
        Map<String, Object> input = new HashMap<>();
        input.put("policyNumber", "POL-TEST");
        when(policyMatchingService.findPolicy(anyString())).thenReturn(Map.of("matched_policy_id", "POL-TEST", "match_score", 90));

        Map<String, Object> result = orchestrationService.transformAndRoute(input);
        assertEquals("Policy_Matched", result.get("fnol_status"));
    }

    @Test
    void emitted_events_fnol_policymatch_completed_reviewrequired() {
        Map<String, Object> input = new HashMap<>();
        input.put("policyNumber", "POL-EVENT");
        when(policyMatchingService.findPolicy(anyString())).thenReturn(Map.of("matched_policy_id", "POL-EVENT", "match_score", 85));

        orchestrationService.transformAndRoute(input);

        verify(eventPublisher, times(1)).publish(eq("FNOL.PolicyMatch.Completed"), any());
    }

    @Test
    void user_visible_outputs_policy_confirmation_message_coverage_summary() {
        Map<String, Object> input = new HashMap<>();
        input.put("policyNumber", "POL-VISIBLE");
        when(policyMatchingService.findPolicy(anyString())).thenReturn(Map.of("matched_policy_id", "POL-VISIBLE", "match_score", 92));

        Map<String, Object> result = orchestrationService.transformAndRoute(input);
        assertNotNull(result.get("policy_confirmation_message"));
        assertNotNull(result.get("coverage_summary"));
    }

    @Test
    void edge_cases_policy_recently_rewritten_or_reinstated_tenant_reporting_landlords_policy_address_changed() {
        Map<String, Object> input = new HashMap<>();
        input.put("policyNumber", "POL-REWRITTEN");
        input.put("edge_case", "REWRITTEN");
        when(policyMatchingService.findPolicy(anyString())).thenReturn(Map.of("matched_policy_id", "POL-REWRITTEN", "match_score", 88));

        Map<String, Object> result = orchestrationService.transformAndRoute(input);
        assertEquals("Policy_Matched", result.get("fnol_status"));

        input.put("policyNumber", "POL-LANDLORD");
        input.put("edge_case", "LANDLORD");
        when(policyMatchingService.findPolicy(anyString())).thenReturn(Map.of("matched_policy_id", "POL-LANDLORD", "match_score", 80));
        result = orchestrationService.transformAndRoute(input);
        assertEquals("Policy_Matched", result.get("fnol_status"));
    }

    @Test
    void negative_scenarios_pas_timeout_or_data_inconsistency_invalid_policy_number_format() {
        when(policyMatchingService.findPolicy(anyString())).thenThrow(new RuntimeException("PAS_TIMEOUT"));
        try {
            orchestrationService.transformAndRoute(Map.of("policyNumber", "POL-TIMEOUT"));
            fail("Expected RuntimeException for PAS timeout");
        } catch (RuntimeException e) {
            assertEquals("PAS_TIMEOUT", e.getMessage());
        }
        verify(auditLogger).logFailure(anyString(), eq("PAS_TIMEOUT"));
    }

    @Test
    void explainability_expectations_score_breakdown_by_criterion_policy_period_comparison_rule_version_applied() {
        Map<String, Object> input = new HashMap<>();
        input.put("policyNumber", "POL-EXPLAIN");
        Map<String, Object> matchResult = new HashMap<>();
        matchResult.put("matched_policy_id", "POL-EXPLAIN");
        matchResult.put("score_breakdown", Map.of("period_match", 0.4, "coverage_match", 0.3, "status_match", 0.2, "other", 0.1));
        matchResult.put("rule_version_applied", "v2.1.0");
        when(policyMatchingService.findPolicy(anyString())).thenReturn(matchResult);

        Map<String, Object> result = orchestrationService.transformAndRoute(input);
        assertNotNull(result.get("score_breakdown_by_criterion"));
        assertEquals("v2.1.0", result.get("rule_version_applied"));
    }

    @Test
    void audit_evidence_input_payload_hash_pas_query_timestamp_rule_version_score_calculation_log() {
        Map<String, Object> input = new HashMap<>();
        input.put("policyNumber", "POL-AUDIT");
        when(policyMatchingService.findPolicy(anyString())).thenReturn(Map.of("matched_policy_id", "POL-AUDIT"));

        orchestrationService.transformAndRoute(input);

        verify(auditLogger).recordEvidence(anyString(), anyString(), anyString());
    }
}
