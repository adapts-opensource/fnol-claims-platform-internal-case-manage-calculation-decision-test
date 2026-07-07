package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Mock integration test for Claim Data Standardization:transformation:orchestration.
 * Verifies output criteria for success paths and absence of failure outputs.
 */
public class ClaimDataStandardizationTransformationOrchestrationMockTest {

    private ClaimDataStoreClient claimDataStoreClient;
    private DocumentManagementClient documentManagementClient;
    private EventPublisher eventPublisher;
    private UiResponseService uiResponseService;
    private TaskService taskService;
    private ClaimDataStandardizationOrchestrationService service;

    @BeforeEach
    void setUp() {
        claimDataStoreClient = mock(ClaimDataStoreClient.class);
        documentManagementClient = mock(DocumentManagementClient.class);
        eventPublisher = mock(EventPublisher.class);
        uiResponseService = mock(UiResponseService.class);
        taskService = mock(TaskService.class);

        service = new ClaimDataStandardizationOrchestrationService(
            claimDataStoreClient,
            documentManagementClient,
            eventPublisher,
            uiResponseService,
            taskService
        );
    }

    @Test
    void output_criteria_success_outputs_standardized_claim_shell_created_policy_match_result_stored_date_validation_result_stored_intake_completion_event_emitted_failure_outputs_validation_error_returned_to_ui_fallback_task_generated_for_manual_review() {
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("claimId", claimId);
        inputPayload.put("policyNumber", "POL-123456");
        inputPayload.put("incidentDate", "2023-11-15");

        service.processClaimStandardization(inputPayload);

        // Success: Standardized claim shell created
        verify(claimDataStoreClient, times(1))
            .putItem(eq("Claim Data Store_table"), argThat(item -> {
                Map<String, Object> attrs = item.getAttributes();
                return attrs.containsKey("pk") &&
                       attrs.containsKey("payload") &&
                       ((Map) attrs.get("payload")).containsKey("standardizedClaimShell");
            }));

        // Success: Policy match result stored
        verify(claimDataStoreClient, times(1))
            .putItem(eq("Claim Data Store_table"), argThat(item -> {
                Map<String, Object> attrs = item.getAttributes();
                return ((Map) attrs.get("payload")).containsKey("policyMatchResult");
            }));

        // Success: Date validation result stored
        verify(claimDataStoreClient, times(1))
            .putItem(eq("Claim Data Store_table"), argThat(item -> {
                Map<String, Object> attrs = item.getAttributes();
                return ((Map) attrs.get("payload")).containsKey("dateValidationResult");
            }));

        // Success: Intake completion event emitted
        verify(eventPublisher, times(1))
            .publish(eq("intake.completion"), argThat(event -> {
                Map<String, Object> evtPayload = event.getPayload();
                return evtPayload.containsKey("claimId") &&
                       evtPayload.get("claimId").equals(claimId);
            }));

        // Failure: Validation error returned to UI (should NOT happen)
        verifyNoInteractions(uiResponseService);

        // Failure: Fallback task generated for manual review (should NOT happen)
        verifyNoInteractions(taskService);
    }
}
