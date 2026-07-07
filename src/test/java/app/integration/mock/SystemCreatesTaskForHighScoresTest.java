package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.HashMap;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock test for Multi-Channel FNOL Submission state transition calculation.
 * Verifies that high-risk/high-score submissions trigger task creation without hitting live infra.
 */
public class MultiChannelFnolStateTransitionCalculationTest {

    @Mock
    private TaskCreationService taskCreationService;
    @Mock
    private ClaimIntakeService claimIntakeService;
    @Mock
    private DataStoreService dataStoreService;
    @Mock
    private CommunicationsHandler communicationsHandler;

    private StateTransitionCalculator calculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        calculator = new StateTransitionCalculator(taskCreationService, claimIntakeService, dataStoreService, communicationsHandler);
    }

    @Test
    void system_creates_task_for_high_scores() {
        // Arrange
        String submissionId = "fnol-9a8b7c";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", submissionId);
        payload.put("calculatedScore", 95); // Threshold typically >= 80
        payload.put("channel", "mobile");

        // Mock infra I/O contracts to prevent live AWS/HTTP calls
        when(claimIntakeService.writePayload(anyString(), anyString())).thenReturn("s3://fnol-bucket/fnol-9a8b7c.json");
        when(dataStoreService.saveItem(anyString(), anyMap())).thenReturn(Map.of("id", submissionId));
        when(communicationsHandler.sendNotification(anyString(), anyList())).thenReturn("ses-msg-456");

        // Act
        calculator.processStateTransition(submissionId, payload);

        // Assert
        ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskCreationService, times(1)).createTask(taskIdCaptor.capture(), eq("HIGH_PRIORITY"));
        assertEquals(submissionId, taskIdCaptor.getValue());

        // Verify infra interactions per contract
        verify(claimIntakeService, times(1)).writePayload(anyString(), anyString());
        verify(dataStoreService, times(1)).saveItem(anyString(), anyMap());
        verify(communicationsHandler, times(1)).sendNotification(anyString(), anyList());
    }
}
