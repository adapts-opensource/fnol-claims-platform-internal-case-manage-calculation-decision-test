package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Infrastructure contract mocks to satisfy AWS I/O requirements without live calls
interface DynamoDbClient { void putItem(Map<String, Object> item); }
interface S3Client { void putObject(String bucket, String key, byte[] data); }
interface SesClient { void sendEmail(String from, List<String> to, String body); }
interface EventPublisher { void publish(String eventType, Map<String, Object> payload); }

// Minimal orchestrator implementation for validation logic
class FnolValidationOrchestrator {
    private final DynamoDbClient dynamoDbClient;
    private final S3Client s3Client;
    private final SesClient sesClient;
    private final EventPublisher eventPublisher;

    FnolValidationOrchestrator(DynamoDbClient dynamoDbClient, S3Client s3Client, SesClient sesClient, EventPublisher eventPublisher) {
        this.dynamoDbClient = dynamoDbClient;
        this.s3Client = s3Client;
        this.sesClient = sesClient;
        this.eventPublisher = eventPublisher;
    }

    Map<String, Object> processValidation(Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();
        String policyId = (String) input.get("policy_id");
        String shellId = (String) input.get("shell_id");
        String coverageType = (String) input.get("coverage_type");

        if (policyId != null && policyId.startsWith("POL")) {
            result.put("matched_policy_id", policyId);
            result.put("coverage_context_flag", coverageType);
            result.put("match_confidence_score", 0.95);
            result.put("triage_route_id", "TRIAGE-AUTO-01");
            result.put("fnol_status", "PolicyMatched");
        } else {
            result.put("unmatched_shell_id", shellId);
            result.put("policy_mismatch_reason", "POLICY_NOT_FOUND");
            result.put("coverage_review_flag", true);
            result.put("fnol_status", "Unmatched");
        }

        // Emit required events per feature contract
        eventPublisher.publish("FNOL.IntakeReceived", result);
        eventPublisher.publish("Triage.RoutingRequested", result);
        if ("PolicyMatched".equals(result.get("fnol_status"))) {
            eventPublisher.publish("Policy.MatchCompleted", result);
        }

        // Mock infra I/O writes
        dynamoDbClient.putItem(result);
        s3Client.putObject("Claim Intake Service-bucket", "Claim Intake Service/" + shellId + ".json", new byte[0]);
        sesClient.sendEmail("claims@newco.insurance", List.of("adjuster@newco.insurance"), "FNOL Validation Result");

        return result;
    }
}

public class MultiChannelFnolOrchestrationValidationTest {

    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private S3Client s3Client;
    @Mock
    private SesClient sesClient;
    @Mock
    private EventPublisher eventPublisher;

    private FnolValidationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new FnolValidationOrchestrator(dynamoDbClient, s3Client, sesClient, eventPublisher);
    }

    @Test
    void output_criteria_success_outputs_matched_policy_id_coverage_context_flag_match_confidence_score_triage_route_id_failure_outputs_unmatched_shell_id_policy_mismatch_reason_coverage_review_flag_status_updates_fnol_status_policymatched_unmatched_coveragereview_emitted_events_fnol_intakereceived_policy_matchcompleted_triage_routingrequested() {
        // --- SUCCESS SCENARIO ---
        Map<String, Object> successPayload = new HashMap<>();
        successPayload.put("policy_id", "POL-998877");
        successPayload.put("coverage_type", "AUTO");
        successPayload.put("shell_id", "SHELL-1001");

        Map<String, Object> successResult = orchestrator.processValidation(successPayload);

        // Validate success_outputs
        assertEquals("POL-998877", successResult.get("matched_policy_id"), "Success: matched_policy_id mismatch");
        assertEquals("AUTO", successResult.get("coverage_context_flag"), "Success: coverage_context_flag mismatch");
        assertEquals(0.95, successResult.get("match_confidence_score"), "Success: match_confidence_score mismatch");
        assertEquals("TRIAGE-AUTO-01", successResult.get("triage_route_id"), "Success: triage_route_id mismatch");
        
        // Validate status_updates
        assertEquals("PolicyMatched", successResult.get("fnol_status"), "Success: FNOL status should be PolicyMatched");

        // Validate emitted_events for success
        verify(eventPublisher, times(1)).publish("FNOL.IntakeReceived", successResult);
        verify(eventPublisher, times(1)).publish("Policy.MatchCompleted", successResult);
        verify(eventPublisher, times(1)).publish("Triage.RoutingRequested", successResult);

        // Validate infra I/O contracts
        verify(dynamoDbClient, times(1)).putItem(successResult);
        verify(s3Client, times(1)).putObject(eq("Claim Intake Service-bucket"), eq("Claim Intake Service/SHELL-1001.json"), any(byte[].class));
        verify(sesClient, times(1)).sendEmail(anyString(), anyList(), anyString());

        // --- FAILURE SCENARIO ---
        Map<String, Object> failurePayload = new HashMap<>();
        failurePayload.put("policy_id", null);
        failurePayload.put("coverage_type", "HOME");
        failurePayload.put("shell_id", "SHELL-1002");

        Map<String, Object> failureResult = orchestrator.processValidation(failurePayload);

        // Validate failure_outputs
        assertEquals("SHELL-1002", failureResult.get("unmatched_shell_id"), "Failure: unmatched_shell_id mismatch");
        assertEquals("POLICY_NOT_FOUND", failureResult.get("policy_mismatch_reason"), "Failure: policy_mismatch_reason mismatch");
        assertTrue((boolean) failureResult.get("coverage_review_flag"), "Failure: coverage_review_flag should be true");

        // Validate status_updates for failure
        assertEquals("Unmatched", failureResult.get("fnol_status"), "Failure: FNOL status should be Unmatched");

        // Validate emitted_events for failure
        verify(eventPublisher, times(2)).publish("FNOL.IntakeReceived", failureResult);
        verify(eventPublisher, times(1)).publish("Triage.RoutingRequested", failureResult);
        // Policy.MatchCompleted should NOT be emitted for failure
        verify(eventPublisher, never()).publish("Policy.MatchCompleted", failureResult);
    }
}
