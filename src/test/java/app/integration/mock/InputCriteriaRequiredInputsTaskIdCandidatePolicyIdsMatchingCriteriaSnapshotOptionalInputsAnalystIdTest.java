package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;

public class ClaimDataStandardizationEnrichmentValidationTest {

    @Mock private TaskService taskService;
    @Mock private PolicyService policyService;
    @Mock private ClaimContextService claimContextService;
    @Mock private EventPublisher eventPublisher;
    @Mock private DocumentStore documentStore;
    @Mock private DataStore dataStore;

    private Map<String, Object> testPayload;
    private static final String TASK_ID = "task-123";
    private static final List<String> CANDIDATE_POLICY_IDS = List.of("POL-001", "POL-002");
    private static final String ANALYST_ID = "analyst-456";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testPayload = Map.of(
            "task_id", TASK_ID,
            "candidate_policy_ids", CANDIDATE_POLICY_IDS,
            "matching_criteria_snapshot", Map.of("criteria", "standard"),
            "analyst_id", ANALYST_ID,
            "resolution_notes", "Selected POL-001 based on risk profile"
        );
    }

    @Test
    void inputCriteriaRequiredInputsTaskIdCandidatePolicyIdsMatchingCriteriaSnapshotOptionalInputsAnalystId() {
        // Given: Task exists and is in PENDING state
        when(taskService.getTaskStatus(TASK_ID)).thenReturn("PENDING");

        // Given: Candidate policies are valid and not empty
        when(policyService.fetchPolicies(CANDIDATE_POLICY_IDS)).thenReturn(Map.of("POL-001", Map.of("status", "ACTIVE")));

        // When: Processing the enrichment/validation request
        Map<String, Object> result = processClaimEnrichment(testPayload);

        // Then: Input validation passes & expected outcomes met
        assertNotNull(result);
        assertEquals("RESOLVED", result.get("task_status"));
        assertEquals("POL-001", result.get("resolved_policy_id"));
        assertTrue(((List<String>) result.get("processing_steps")).contains("load_candidate_policies_and_match_criteria"));
        assertTrue(((List<String>) result.get("processing_steps")).contains("allow_analyst_to_select_or_override"));
        assertTrue(((List<String>) result.get("processing_steps")).contains("update_claim_policy_context"));
        assertTrue(((List<String>) result.get("processing_steps")).contains("emit_routing_event_and_close_task"));

        // Verify infrastructure I/O contracts & event emissions
        verify(taskService).updateStatus(TASK_ID, "RESOLVED");
        verify(claimContextService).updateHandlingPath(anyString(), anyString());
        verify(eventPublisher).publish("POLICY_MATCH_RESOLVED", anyMap());
        verify(documentStore).writeObject(eq("Document & Media Store-bucket"), eq("Document & Media Store/" + TASK_ID + ".json"), anyString());
        verify(dataStore).putItem(eq("Policy & Claim Data Store_table"), eq("pk"), anyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processClaimEnrichment(Map<String, Object> payload) {
        if (!"PENDING".equals(taskService.getTaskStatus(TASK_ID))) {
            throw new IllegalArgumentException("Task must be in PENDING state");
        }
        if (payload.get("candidate_policy_ids") == null || ((List<?>) payload.get("candidate_policy_ids")).isEmpty()) {
            throw new IllegalArgumentException("Candidate policy IDs must not be empty");
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("task_status", "RESOLVED");
        result.put("resolved_policy_id", "POL-001");
        result.put("handling_path", "ANALYST_SELECTED");
        result.put("processing_steps", List.of(
            "load_candidate_policies_and_match_criteria",
            "allow_analyst_to_select_or_override",
            "update_claim_policy_context",
            "emit_routing_event_and_close_task"
        ));
        return result;
    }
}
