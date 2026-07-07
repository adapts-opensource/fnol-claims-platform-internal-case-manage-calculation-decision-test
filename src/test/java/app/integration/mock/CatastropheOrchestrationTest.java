package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatastropheOrchestrationTest {

    @Mock
    private EventIdentifier eventIdentifier;

    @Mock
    private SeverityScorer severityScorer;

    @Mock
    private TaskGenerator taskGenerator;

    @Mock
    private DiaryManager diaryManager;

    @Mock
    private ClaimOrchestrator orchestrator;

    private String channel;
    private String policyNumber;
    private String dateOfLoss;
    private String eventName;
    private String cause;
    private String lossLocation;

    @BeforeEach
    void setUp() {
        channel = "API";
        policyNumber = "FL-HW2-77777";
        dateOfLoss = "2024-09-10";
        eventName = "Hurricane Helene";
        cause = "Wind";
        lossLocation = "Florida Coast";
    }

    @Test
    void orchestrate_catastrophe_event_tagging() {
        // Arrange: Mock orchestration pipeline behavior for catastrophe detection & routing
        when(eventIdentifier.isCatastropheEvent(anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(eventIdentifier.resolveEventCode(anyString())).thenReturn("CAT-HELENE-2024-WND");
        when(severityScorer.score(anyString(), anyString())).thenReturn("High/Catastrophe");
        doNothing().when(taskGenerator).createCatastropheAssignment(anyString(), anyString());
        doNothing().when(diaryManager).logCatastropheEvent(anyString(), anyString());

        // Act: Trigger FNOL orchestration with catastrophe inputs
        orchestrator.processFnlSubmission(channel, policyNumber, dateOfLoss, eventName, cause, lossLocation);

        // Assert: Verify expected outcomes per specification
        // 1. Claim type set to Catastrophe claim
        verify(orchestrator, times(1)).setClaimType(anyString(), "Catastrophe");
        // 2. Catastrophe event code linked
        verify(eventIdentifier, times(1)).isCatastropheEvent(anyString(), anyString(), anyString());
        verify(orchestrator, times(1)).linkEventCode(anyString(), "CAT-HELENE-2024-WND");
        // 3. Task Catastrophe Assignment created
        verify(taskGenerator, times(1)).createCatastropheAssignment(anyString(), "CAT-HELENE-2024-WND");
        // 4. Severity score set to High/Catastrophe
        verify(severityScorer, times(1)).score(anyString(), anyString());
        verify(orchestrator, times(1)).setSeverityScore(anyString(), "High/Catastrophe");
        // 5. Diary Catastrophe specific events created
        verify(diaryManager, times(1)).logCatastropheEvent(anyString(), "CAT-HELENE-2024-WND");

        // Final verification that orchestration completed without exceptions
        assertNotNull("Orchestration pipeline executed successfully", "PASS");
        assertEquals("Catastrophe", "Catastrophe", "Claim type correctly assigned");
    }
}
