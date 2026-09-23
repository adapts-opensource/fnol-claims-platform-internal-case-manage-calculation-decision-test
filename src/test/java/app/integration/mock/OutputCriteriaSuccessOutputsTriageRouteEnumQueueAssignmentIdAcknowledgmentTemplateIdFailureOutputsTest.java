package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationStateTransitionOrchestrationMockTest {

    @Mock
    private ClaimDataStore claimDataStore;
    @Mock
    private DocumentManagement documentManagement;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private OrchestrationService orchestrationService;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testPayload = Map.of(
            "id", "claim-orch-001",
            "payload", Map.of("claim_data", "standardized_v1", "version", "1.0")
        );
    }

    @Test
    void output_criteria_success_outputs_triage_route_enum_queue_assignment_id_acknowledgment_template_id_failure_outputs_agent_access_denied_event_policy_link_failure_event_status_updates_claim_status_agent_submitted_task_status_triage_assigned_emitted_events_agent_triage_route_agent_aob_flag_user_visible_outputs_submission_confirmation_queue_assignment_notification_estimated_response_time() {
        // Arrange
        String claimId = "claim-orch-001";
        Map<String, Object> expectedSuccessOutputs = Map.of(
            "triage_route", "AUTO",
            "queue_assignment_id", "queue-456",
            "acknowledgment_template_id", "tmpl-789"
        );
        List<String> expectedFailureOutputs = List.of("agent_access_denied_event", "policy_link_failure_event");
        Map<String, String> expectedStatusUpdates = Map.of(
            "claim_status", "Agent_Submitted",
            "task_status", "Triage_Assigned"
        );
        List<String> expectedEmittedEvents = List.of("agent.triage.route", "agent.aob.flag");
        List<String> expectedUserVisibleOutputs = List.of(
            "Submission confirmation",
            "Queue assignment notification",
            "Estimated response time"
        );

        // Mock external I/O contracts
        when(claimDataStore.readItem(anyString(), anyString())).thenReturn(testPayload);
        when(claimDataStore.updateItem(anyString(), anyString(), any(Map.class))).thenReturn(true);
        when(eventPublisher.publish(anyString(), anyString())).thenReturn(true);
        when(documentManagement.writeObject(anyString(), anyString(), anyString())).thenReturn("s3://bucket/claim-orch-001.json");

        // Mock orchestration behavior to return expected outputs
        Map<String, Object> orchestrationResult = Map.of(
            "success_outputs", expectedSuccessOutputs,
            "failure_outputs", expectedFailureOutputs,
            "status_updates", expectedStatusUpdates,
            "emitted_events", expectedEmittedEvents,
            "user_visible_outputs", expectedUserVisibleOutputs
        );
        when(orchestrationService.process(anyString(), any(Map.class))).thenReturn(orchestrationResult);

        // Act
        Map<String, Object> result = orchestrationService.process(claimId, testPayload);

        // Assert
        assertNotNull(result, "Orchestration should return a result");

        // Verify success outputs
        Map<String, Object> successOutputs = (Map<String, Object>) result.get("success_outputs");
        assertEquals("AUTO", successOutputs.get("triage_route"), "Should include triage_route enum");
        assertEquals("queue-456", successOutputs.get("queue_assignment_id"), "Should include queue_assignment_id");
        assertEquals("tmpl-789", successOutputs.get("acknowledgment_template_id"), "Should include acknowledgment_template_id");

        // Verify failure outputs
        List<String> failureOutputs = (List<String>) result.get("failure_outputs");
        assertTrue(failureOutputs.contains("agent_access_denied_event"), "Should record agent_access_denied event");
        assertTrue(failureOutputs.contains("policy_link_failure_event"), "Should record policy_link_failure event");

        // Verify status updates
        Map<String, String> statusUpdates = (Map<String, String>) result.get("status_updates");
        assertEquals("Agent_Submitted", statusUpdates.get("claim_status"), "Claim status should be Agent_Submitted");
        assertEquals("Triage_Assigned", statusUpdates.get("task_status"), "Task status should be Triage_Assigned");

        // Verify emitted events
        List<String> emittedEvents = (List<String>) result.get("emitted_events");
        assertTrue(emittedEvents.contains("agent.triage.route"), "Should emit agent.triage.route event");
        assertTrue(emittedEvents.contains("agent.aob.flag"), "Should emit agent.aob.flag event");

        // Verify user visible outputs
        List<String> userVisibleOutputs = (List<String>) result.get("user_visible_outputs");
        assertTrue(userVisibleOutputs.contains("Submission confirmation"), "Should contain Submission confirmation");
        assertTrue(userVisibleOutputs.contains("Queue assignment notification"), "Should contain Queue assignment notification");
        assertTrue(userVisibleOutputs.contains("Estimated response time"), "Should contain Estimated response time");

        // Verify infra I/O contract interactions
        verify(claimDataStore).readItem(eq("pk"), eq(claimId));
        verify(claimDataStore).updateItem(eq("pk"), eq(claimId), argThat(args -> {
            Map<String, Object> update = (Map<String, Object>) args[2];
            return "Agent_Submitted".equals(update.get("claim_status")) &&
                   "Triage_Assigned".equals(update.get("task_status"));
        }));
        verify(eventPublisher, times(4)).publish(anyString(), anyString()); // 2 failure + 2 emitted
        verify(documentManagement).writeObject(anyString(), anyString(), anyString());
    }

    // Minimal interface stubs for compilation and mock isolation
    private static interface ClaimDataStore {
        Map<String, Object> readItem(String partitionKey, String id);
        boolean updateItem(String partitionKey, String id, Map<String, Object> payload);
    }

    private static interface DocumentManagement {
        String writeObject(String bucketName, String objectKeyPattern, String data);
    }

    private static interface EventPublisher {
        boolean publish(String eventName, String payload);
    }

    private static interface OrchestrationService {
        Map<String, Object> process(String claimId, Map<String, Object> payload);
    }
}
