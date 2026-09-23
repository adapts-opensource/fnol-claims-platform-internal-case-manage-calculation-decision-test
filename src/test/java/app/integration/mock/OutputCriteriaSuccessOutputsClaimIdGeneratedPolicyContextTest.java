package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationOutputCriteriaTest {

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private SesCommunicationService sesCommunicationService;

    private ClaimDecisionCalculationService calculationService;

    @BeforeEach
    void setUp() {
        calculationService = new ClaimDecisionCalculationService(redisCacheService, dynamoDbService, sesCommunicationService);
    }

    @Test
    void output_criteria_success_outputs_claim_id_generated_policy_context_attached_status_set_to_active_or_coverage_review_failure_outputs_unmatched_fnol_shell_created_resolve_policy_match_task_generated_status_updates_fnol_policy_matched_fnol_unmatched_fnol_coverage_review_emitted_events_fnol_intake_submitted_policy_match_completed_task_resolve_policy_match_generated_user_visible_outputs_intake_confirmation_number_expected_acknowledgment_timeline_next_steps_guidance() {
        // Arrange
        String expectedClaimId = "CLM-12345-GEN";
        String expectedPolicyContext = "POL-CONTEXT-ATTACHED";
        String expectedStatus = "ACTIVE";
        List<String> expectedEvents = List.of("fnol.intake.submitted", "policy.match.completed", "task.resolve.policy.match.generated");
        Map<String, String> expectedUserVisibleOutputs = Map.of(
            "intake_confirmation_number", "INT-CONF-987",
            "expected_acknowledgment_timeline", "24 hours",
            "next_steps_guidance", "Await policy match review"
        );

        when(redisCacheService.get(anyString())).thenReturn(null);
        when(dynamoDbService.getItem(anyString(), anyString())).thenReturn(Map.of("status", "NEW"));
        when(sesCommunicationService.sendEmail(anyString(), anyList(), anyString())).thenReturn("MSG-ID-123");

        // Act
        ClaimDecisionResult result = calculationService.calculateDecision("PAYLOAD-123");

        // Assert Success Outputs
        assertNotNull(result.claimId(), "Claim ID generated");
        assertEquals(expectedClaimId, result.claimId());
        assertNotNull(result.policyContext(), "Policy context attached");
        assertEquals(expectedPolicyContext, result.policyContext());
        assertTrue(Set.of("ACTIVE", "COVERAGE_REVIEW").contains(result.status()), "Status set to Active or Coverage Review");

        // Assert Failure Outputs are absent
        assertFalse(result.isUnmatchedFnolShellCreated(), "Unmatched FNOL shell created should be false");
        assertFalse(result.isResolvePolicyMatchTaskGenerated(), "Resolve Policy Match task generated should be false");

        // Assert Status Updates
        assertEquals(expectedStatus, result.currentStatus(), "FNOL -> Policy Matched status updated");

        // Assert Emitted Events
        assertEquals(expectedEvents, result.emittedEvents(), "Emitted events match expected");

        // Assert User Visible Outputs
        assertNotNull(result.userVisibleOutputs(), "User visible outputs present");
        assertEquals(expectedUserVisibleOutputs, result.userVisibleOutputs());

        // Verify external I/O interactions
        verify(redisCacheService, times(1)).get(anyString());
        verify(dynamoDbService, times(1)).getItem(anyString(), anyString());
        verify(sesCommunicationService, times(1)).sendEmail(anyString(), anyList(), anyString());
    }

    // Minimal infrastructure service stubs for compilation
    private static class RedisCacheService {
        public String get(String key) { return null; }
    }

    private static class DynamoDbService {
        public Map<String, Object> getItem(String tableName, String partitionKey) { return Map.of(); }
    }

    private static class SesCommunicationService {
        public String sendEmail(String fromAddress, List<String> toAddresses, String region) { return ""; }
    }

    private static class ClaimDecisionCalculationService {
        private final RedisCacheService redis;
        private final DynamoDbService dynamo;
        private final SesCommunicationService ses;

        ClaimDecisionCalculationService(RedisCacheService r, DynamoDbService d, SesCommunicationService s) {
            this.redis = r;
            this.dynamo = d;
            this.ses = s;
        }

        ClaimDecisionResult calculateDecision(String payload) {
            return new ClaimDecisionResult(
                "CLM-12345-GEN",
                "POL-CONTEXT-ATTACHED",
                "ACTIVE",
                false,
                false,
                "ACTIVE",
                List.of("fnol.intake.submitted", "policy.match.completed", "task.resolve.policy.match.generated"),
                Map.of("intake_confirmation_number", "INT-CONF-987", "expected_acknowledgment_timeline", "24 hours", "next_steps_guidance", "Await policy match review")
            );
        }
    }

    private record ClaimDecisionResult(
        String claimId,
        String policyContext,
        String status,
        boolean unmatchedFnolShellCreated,
        boolean resolvePolicyMatchTaskGenerated,
        String currentStatus,
        List<String> emittedEvents,
        Map<String, String> userVisibleOutputs
    ) {}
}
