package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationStateTransitionOrchestrationTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;
    @Mock
    private DocumentManagementClient documentManagementClient;
    @Mock
    private RulesTriageServiceClient rulesTriageServiceClient;

    @InjectMocks
    private ClaimDataStandardizationStateTransitionOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and lifecycle
    }

    @Test
    void output_criteria_success_outputs_merge_result_or_continue_result_decision_log_id_updated_claim_status_failure_outputs_invalid_decision_event_policy_conflict_event_status_updates_duplicate_flag_status_resolved_claim_status_merged_or_active_emitted_events_duplicate_review_completed_duplicate_merge_executed_user_visible_outputs_decision_confirmation_updated_claim_status_notification_to_original_claim_owner() {
        // Arrange
        String claimId = "claim-12345";
        Map<String, Object> inputPayload = Map.of(
                "id", claimId,
                "claim_status", "Pending",
                "duplicate_flag_status", "Flagged",
                "merge_result", true
        );

        // Mock DynamoDB interactions for Claim Data Store
        when(claimDataStoreClient.getItem(any(Map.class))).thenReturn(Map.of("id", claimId, "status", "Pending"));
        when(claimDataStoreClient.updateItem(any(Map.class))).thenReturn(Map.of("Attributes", Map.of("claim_status", "Merged", "duplicate_flag_status", "Resolved")));

        // Mock S3 interactions for Document Management
        when(documentManagementClient.putObject(any(Map.class))).thenReturn("s3://document-management-bucket/claim-12345.json");

        // Mock Rules & Triage Service
        when(rulesTriageServiceClient.evaluateRules(any(Map.class))).thenReturn(Map.of("decision_log_id", "dec-log-789"));

        // Act
        Map<String, Object> result = orchestrationService.processTransition(claimId, inputPayload);

        // Assert Success Outputs
        List<String> successOutputs = (List<String>) result.get("success_outputs");
        assertNotNull(successOutputs, "Success outputs must be present");
        assertTrue(successOutputs.contains("merge_result or continue_result") || successOutputs.contains("continue_result"), "Should contain merge_result or continue_result");
        assertTrue(successOutputs.contains("decision_log_id"), "Should contain decision_log_id");
        assertTrue(successOutputs.contains("updated_claim_status"), "Should contain updated_claim_status");

        // Assert Failure Outputs NOT present
        List<String> failureOutputs = (List<String>) result.get("failure_outputs");
        assertNotNull(failureOutputs, "Failure outputs list must be present");
        assertTrue(failureOutputs.isEmpty(), "Failure outputs should be empty for success path");
        assertFalse(failureOutputs.contains("invalid_decision event"), "Should not emit invalid_decision event");
        assertFalse(failureOutputs.contains("policy_conflict event"), "Should not emit policy_conflict event");

        // Assert Status Updates
        @SuppressWarnings("unchecked")
        Map<String, String> statusUpdates = (Map<String, String>) result.get("status_updates");
        assertEquals("Resolved", statusUpdates.get("duplicate_flag_status"), "duplicate_flag_status should be Resolved");
        assertTrue(List.of("Merged", "Active").contains(statusUpdates.get("claim_status")), "claim_status should be Merged or Active");

        // Assert Emitted Events
        List<String> emittedEvents = (List<String>) result.get("emitted_events");
        assertNotNull(emittedEvents, "Emitted events must be present");
        assertTrue(emittedEvents.contains("duplicate.review.completed"), "Should emit duplicate.review.completed");
        assertTrue(emittedEvents.contains("duplicate.merge.executed"), "Should emit duplicate.merge.executed");

        // Assert User Visible Outputs
        List<String> userVisibleOutputs = (List<String>) result.get("user_visible_outputs");
        assertNotNull(userVisibleOutputs, "User visible outputs must be present");
        assertTrue(userVisibleOutputs.contains("Decision confirmation"), "Should show Decision confirmation");
        assertTrue(userVisibleOutputs.contains("Updated claim status"), "Should show Updated claim status");
        assertTrue(userVisibleOutputs.contains("Notification to original claim owner"), "Should show Notification to original claim owner");

        // Verify external I/O contracts were invoked exactly as expected
        verify(claimDataStoreClient, times(1)).updateItem(any());
        verify(documentManagementClient, times(1)).putObject(any());
        verify(rulesTriageServiceClient, times(1)).evaluateRules(any());
    }
}
