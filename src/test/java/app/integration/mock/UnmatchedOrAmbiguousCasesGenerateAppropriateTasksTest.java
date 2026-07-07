package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UnmatchedOrAmbiguousCasesGenerateAppropriateTasksTest {

    @Mock
    private DecisionEnrichmentProcessor enrichmentProcessor;

    @Mock
    private TaskGenerationService taskService;

    private String claimId;
    private Map<String, Object> ambiguousPayload;

    @BeforeEach
    void setUp() {
        claimId = "CLM-AMB-2024-001";
        ambiguousPayload = Map.of(
            "id", claimId,
            "standardizationStatus", "UNMATCHED",
            "confidenceScore", 0.35,
            "missingCriticalFields", List.of("POLICY_NUMBER", "INCIDENT_DATE"),
            "payload", Map.of("rawClaimData", "sample_json")
        );
    }

    @Test
    void unmatched_or_ambiguous_cases_generate_appropriate_tasks() {
        // Arrange: Mock decision processor to indicate review is required for ambiguous data
        when(enrichmentProcessor.evaluateDecision(ambiguousPayload))
            .thenReturn(new DecisionResult(claimId, DecisionOutcome.REVIEW_REQUIRED));

        ArgumentCaptor<TaskRequest> taskRequestCaptor = ArgumentCaptor.forClass(TaskRequest.class);

        // Act: Process claim and trigger task generation
        List<TaskRequest> generatedTasks = enrichmentProcessor.processAndGenerateTasks(ambiguousPayload);

        // Assert: Verify tasks were generated with appropriate properties
        assertNotNull(generatedTasks);
        assertEquals(1, generatedTasks.size());

        TaskRequest request = generatedTasks.get(0);
        assertEquals(claimId, request.claimId());
        assertEquals("CLAIM_REVIEW", request.taskType());
        assertEquals("PENDING", request.status());
        assertTrue(request.metadata().containsKey("ambiguityReason"));

        // Verify downstream task service was invoked with correct contract
        verify(taskService, times(1)).createTask(taskRequestCaptor.capture());
        TaskRequest capturedTask = taskRequestCaptor.getValue();
        assertEquals(claimId, capturedTask.claimId());
        assertEquals("CLAIM_REVIEW", capturedTask.taskType());
    }

    // Minimal domain stubs to keep test self-contained and compilable
    record DecisionResult(String claimId, DecisionOutcome outcome) {}
    enum DecisionOutcome { REVIEW_REQUIRED, STANDARDIZED, REJECTED }
    record TaskRequest(String claimId, String taskType, String status, Map<String, Object> metadata) {}
}
