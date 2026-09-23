package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttorneyRepresentationRoutingTest {

    @Mock
    private ClaimService claimService;

    @Mock
    private TaskService taskService;

    @Mock
    private DiaryService diaryService;

    @Mock
    private CommunicationService communicationService;

    @InjectMocks
    private InsuredEngagementOrchestrator orchestrator;

    @Test
    void orchestrate_attorney_representation_routing() {
        // Arrange
        String reporterType = "attorney";
        String attorneyName = "Jane Smith";
        String claimNumber = "CLM-11223";
        String policyNumber = "POL-54321";
        String communicationChannel = "email";

        when(claimService.getClaim(claimNumber)).thenReturn(new Claim(claimNumber, policyNumber));

        // Act
        RoutingResult result = orchestrator.processAttorneyEngagement(
                reporterType, attorneyName, claimNumber, policyNumber, communicationChannel
        );

        // Assert
        assertNotNull(result);
        assertTrue(result.isAttorneyRepresented(), "Claim should be flagged as attorney represented");
        assertTrue(result.isDirectCommunicationRestricted(), "Direct communications should be restricted");
        assertEquals("Attorney Representation Review", result.getTaskTitle(), "Task title mismatch");
        assertEquals("Representation document due", result.getDiaryTitle(), "Diary title mismatch");
        assertEquals("represented-claim", result.getWorkflowRoute(), "Communication workflow route mismatch");

        // Verify
        verify(claimService).flagClaim(claimNumber, true);
        verify(communicationService).restrictDirectCommunication(claimNumber);
        verify(taskService).createTask(eq(claimNumber), eq("Attorney Representation Review"), eq(attorneyName));
        verify(diaryService).createDiary(eq(claimNumber), eq("Representation document due"), anyString());
        verify(communicationService).routeCommunication(eq(claimNumber), eq(communicationChannel), eq("represented-claim"));
    }
}
