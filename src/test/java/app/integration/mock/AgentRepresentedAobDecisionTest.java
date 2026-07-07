package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class AgentRepresentedAobDecisionTest {

    @Mock
    private ClaimStateService claimStateService;
    @Mock
    private TaskService taskService;
    @Mock
    private CommunicationService communicationService;
    @Mock
    private DiaryService diaryService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private S3Client s3Client;
    @Mock
    private SesClient sesClient;
    @Mock
    private DynamoDbClient dynamoDbClient;

    private MultiChannelFnolOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new MultiChannelFnolOrchestrationService(
            claimStateService, taskService, communicationService,
            diaryService, auditLogService, s3Client, sesClient, dynamoDbClient
        );
    }

    @Test
    void orchestrate_agent_represented_aob_fnol_decision() {
        // Arrange: Prepare inputs matching the test case description
        Map<String, Object> fnolPayload = new HashMap<>();
        fnolPayload.put("channel", "Agent Portal");
        fnolPayload.put("product", "HO3");
        fnolPayload.put("cause_of_loss", "Wind");
        fnolPayload.put("severity", "High");
        fnolPayload.put("attorney_flag", true);
        fnolPayload.put("pa_flag", false);
        fnolPayload.put("aob_flag", true);
        fnolPayload.put("sinkhole_indicator", false);
        fnolPayload.put("injuries", false);

        String claimId = "FNOL-AGENT-001";

        // Act: Execute orchestration
        orchestrationService.processFnolSubmission(claimId, fnolPayload);

        // Assert: Verify expected orchestration decisions
        // 1. Claim state transitions to Claim Opened
        verify(claimStateService).transitionState(eq(claimId), eq("Claim Opened"));

        // 2. Initial claim type set to Represented claim
        verify(claimStateService).setClaimType(eq(claimId), eq("Represented"));

        // 3. Conditional tasks created for Attorney Representation Review and AOB Review
        verify(taskService).createTask(eq(claimId), eq("Attorney Representation Review"));
        verify(taskService).createTask(eq(claimId), eq("AOB Review"));

        // 4. Communication routing restricted to attorney
        verify(communicationService).setRoutingPolicy(eq(claimId), eq("ATTORNEY_ONLY"));

        // 5. Diary for representation document due created
        verify(diaryService).createDiaryEntry(eq(claimId), eq("Representation Document Due"));

        // 6. Audit log records representative capacity
        ArgumentCaptor<String> auditMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logEvent(eq(claimId), auditMessageCaptor.capture());
        assertTrue(auditMessageCaptor.getValue().contains("representative capacity"),
                "Audit log should record representative capacity");
    }

    // Minimal domain interfaces for compilation and isolation
    interface ClaimStateService {
        void transitionState(String claimId, String state);
        void setClaimType(String claimId, String type);
    }
    interface TaskService {
        void createTask(String claimId, String taskName);
    }
    interface CommunicationService {
        void setRoutingPolicy(String claimId, String policy);
    }
    interface DiaryService {
        void createDiaryEntry(String claimId, String entryName);
    }
    interface AuditLogService {
        void logEvent(String claimId, String message);
    }
    interface S3Client {
        void putObject(String bucket, String key, Object body);
    }
    interface SesClient {
        void sendEmail(String from, List<String> to, String region);
    }
    interface DynamoDbClient {
        void putItem(String table, Map<String, Object> item);
    }
}
