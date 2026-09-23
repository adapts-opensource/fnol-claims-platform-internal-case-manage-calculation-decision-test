package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionStateTransitionCalculationTest {

    @Mock
    private DataStoreClient dataStoreClient;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private MultiChannelFnolSubmissionProcessor processor;

    @Test
    void system_creates_task_when_multiple_matches_found() {
        // Given: Payload simulating multiple matches found during state transition calculation
        String submissionId = "sub-98765";
        Map<String, Object> payload = Map.of(
            "id", submissionId,
            "matches", List.of("claim-ref-1", "claim-ref-2", "claim-ref-3")
        );

        when(dataStoreClient.readPayload(submissionId)).thenReturn(payload);

        // When: System processes state transition calculation
        processor.calculate(submissionId);

        // Then: Verify that a task was created due to multiple matches
        ArgumentCaptor<Map<String, Object>> taskPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(taskService, times(1)).createTask(taskPayloadCaptor.capture());

        Map<String, Object> capturedTask = taskPayloadCaptor.getValue();
        assertEquals(submissionId, capturedTask.get("referenceId"));
        assertEquals("MULTIPLE_MATCHES", capturedTask.get("taskType"));
        
        // Verify data store was accessed correctly
        verify(dataStoreClient).readPayload(submissionId);
    }
}
