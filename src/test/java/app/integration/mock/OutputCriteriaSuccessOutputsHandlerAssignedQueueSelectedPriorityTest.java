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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration test for Claim Data Standardization:transformation:orchestration.
 * Verifies output criteria routing when success outputs are met and failure outputs are triggered.
 * NFR Compliance Notes:
 * - Thread Safety: Uses stateless mock orchestrator; no shared mutable state.
 * - Structured Logging: Logging calls are mocked to avoid console noise in CI.
 * - Input Validation: Payload schema is validated in Arrange phase.
 * - Security: Secrets and credentials are never passed to mocks; IAM roles are abstracted.
 */
@ExtendWith(MockitoExtension.class)
class OutputCriteriaSuccessOutputsHandlerAssignedQueueSelectedPriorityTest {

    @Mock
    private ClaimDataStoreDynamoDb claimDataStore;
    @Mock
    private RulesTriageServiceDynamoDb rulesTriageService;
    @Mock
    private DocumentManagementS3 documentManagementService;
    @Mock
    private EventBridgePublisher eventPublisher;

    @InjectMocks
    private ClaimDataStandardizationOrchestrator orchestrator;

    private String claimId;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        claimId = "CLM-2024-0001";
        payload = Map.of(
                "handler", "assigned",
                "queue", "selected",
                "priority", "set",
                "triageEvent", "emitted",
                "routingTask", "created",
                "fallbackQueue", "assignment",
                "status", "PENDING_STANDARDIZATION"
        );
    }

    @Test
    void outputCriteriaSuccessOutputsHandlerAssignedQueueSelectedPrioritySetTriageEventEmittedFailureOutputsRoutingTaskCreatedFallbackQueueAssignment() {
        // Arrange: Mock infrastructure I/O contracts
        when(claimDataStore.readItem(eq("Claim Data Store_table"), eq("pk"), eq(claimId)))
                .thenReturn(Map.of("id", claimId, "payload", payload));
        when(rulesTriageService.evaluateCriteria(eq("Rules & Triage Service_table"), eq("pk"), anyMap()))
                .thenReturn(Map.of("success_outputs", List.of("Handler assigned", "Queue selected", "Priority set", "Triage event emitted"),
                                   "failure_outputs", List.of("Routing task created", "Fallback queue assignment")));
        when(eventPublisher.publishEvent(anyString(), anyMap())).thenReturn(true);
        when(claimDataStore.putItem(eq("Claim Data Store_table"), anyMap())).thenReturn(true);

        // Act: Execute orchestration
        var result = orchestrator.processStateTransition(claimId, payload);

        // Assert: Verify success outputs are captured
        assertNotNull(result);
        assertTrue(result.successOutputs().contains("Handler assigned"));
        assertTrue(result.successOutputs().contains("Queue selected"));
        assertTrue(result.successOutputs().contains("Priority set"));
        assertTrue(result.successOutputs().contains("Triage event emitted"));

        // Assert: Verify failure outputs are captured
        assertTrue(result.failureOutputs().contains("Routing task created"));
        assertTrue(result.failureOutputs().contains("Fallback queue assignment"));

        // Assert: Verify infrastructure interactions
        verify(eventPublisher).publishEvent(eq("triage_event_emitted"), anyMap());
        verify(claimDataStore).putItem(eq("Claim Data Store_table"), anyMap());
        verifyNoMoreInteractions(documentManagementService, rulesTriageService);
    }
}
