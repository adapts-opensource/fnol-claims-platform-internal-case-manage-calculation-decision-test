package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Minimal domain stubs for compilation context
class PAEngagementPayload {
    String reporterType;
    String paName;
    String paLicense;
    String paContact;
    boolean paInvolved;
}

interface ClaimPartyService { void createParty(String type, String name, String license, String contact); }
interface TaskService { void createTask(String title, String assignee); }
interface CommunicationLogService { void logNotification(String recipient, String message); }
interface ClaimTriageService { void setFlag(String flag); }

class PAMapperService {
    private final ClaimPartyService claimPartyService;
    private final TaskService taskService;
    private final CommunicationLogService communicationLogService;
    private final ClaimTriageService claimTriageService;

    public PAMapperService(ClaimPartyService claimPartyService, TaskService taskService,
                           CommunicationLogService communicationLogService, ClaimTriageService claimTriageService) {
        this.claimPartyService = claimPartyService;
        this.taskService = taskService;
        this.communicationLogService = communicationLogService;
        this.claimTriageService = claimTriageService;
    }

    public void transform(PAEngagementPayload input) {
        if (input.paInvolved) {
            claimPartyService.createParty("PA", input.paName, input.paLicense, input.paContact);
            taskService.createTask("Review PA Documentation", "Claims Intake");
            communicationLogService.logNotification(input.paContact, "PA notification sent");
            claimTriageService.setFlag("Represented claim");
        }
    }
}

@ExtendWith(MockitoExtension.class)
public class PAMapperTaskGenTest {
    @Mock ClaimPartyService claimPartyService;
    @Mock TaskService taskService;
    @Mock CommunicationLogService communicationLogService;
    @Mock ClaimTriageService claimTriageService;
    @InjectMocks PAMapperService mapperService;

    @Captor ArgumentCaptor<String> partyTypeCaptor;
    @Captor ArgumentCaptor<String> taskTitleCaptor;
    @Captor ArgumentCaptor<String> assigneeCaptor;
    @Captor ArgumentCaptor<String> recipientCaptor;
    @Captor ArgumentCaptor<String> triageFlagCaptor;

    @BeforeEach
    void setUp() {
        // MockitoExtension initializes mocks and performs injection
    }

    @Test
    void transform_pa_involved_flag_to_tasks() {
        // Arrange
        PAEngagementPayload payload = new PAEngagementPayload();
        payload.reporterType = "Public Adjuster";
        payload.paName = "PA Firm LLC";
        payload.paLicense = "PA-999";
        payload.paContact = "pa@firm.com";
        payload.paInvolved = true;

        // Act
        mapperService.transform(payload);

        // Assert: Claim Party created with type PA
        verify(claimPartyService).createParty(partyTypeCaptor.capture(), anyString(), anyString(), anyString());
        assertEquals("PA", partyTypeCaptor.getValue());

        // Assert: Task 'Review PA Documentation' generated assigned to Claims Intake
        verify(taskService).createTask(taskTitleCaptor.capture(), assigneeCaptor.capture());
        assertEquals("Review PA Documentation", taskTitleCaptor.getValue());
        assertEquals("Claims Intake", assigneeCaptor.getValue());

        // Assert: Communication log indicates PA notification sent
        verify(communicationLogService).logNotification(recipientCaptor.capture(), anyString());
        assertEquals("pa@firm.com", recipientCaptor.getValue());

        // Assert: Claim triage flags 'Represented claim'
        verify(claimTriageService).setFlag(triageFlagCaptor.capture());
        assertEquals("Represented claim", triageFlagCaptor.getValue());
    }
}
