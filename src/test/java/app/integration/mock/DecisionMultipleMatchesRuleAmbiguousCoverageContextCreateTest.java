package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock integration test for Claim Data Standardization:state_transition:orchestration.
 * Verifies rule evaluation and external I/O mocking without live AWS/HTTP calls.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationStateTransitionOrchestrationTest {

    @Mock
    private ClaimDataRepository claimDataRepository;
    @Mock
    private TaskCreationService taskCreationService;
    @Mock
    private StateTransitionService stateTransitionService;

    private ClaimOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimOrchestrationService(claimDataRepository, taskCreationService, stateTransitionService);
    }

    @Test
    void decision_multiple_matches_rule_ambiguous_coverage_context_create_resolve_policy_match_task_expected_outcome_route_to_coverage_review() {
        // Arrange: Mock claim payload simulating multiple matches & ambiguous coverage context
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> claimPayload = Map.of(
            "multiple_matches", true,
            "ambiguous_coverage_context", true,
            "policy_match_count", 2,
            "coverage_type", "AUTO"
        );

        when(claimDataRepository.fetchClaimData(eq(claimId))).thenReturn(claimPayload);

        // Act: Execute orchestration logic
        orchestrationService.evaluateAndTransition(claimId);

        // Assert: Verify task creation and state transition interactions
        verify(taskCreationService).createTask(eq(claimId), eq("RESOLVE_POLICY_MATCH"), any(String.class));
        verify(stateTransitionService).transitionTo(eq(claimId), eq("COVERAGE_REVIEW"));
        verify(claimDataRepository).fetchClaimData(eq(claimId));
        assertNotNull(claimId);
    }
}
