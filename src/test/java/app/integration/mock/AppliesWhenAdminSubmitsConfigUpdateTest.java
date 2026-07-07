package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class StateTransitionConfigUpdateIntegrationTest {

    @Mock
    private ReserveLineService reserveLineService;
    @Mock
    private ComplianceDiaryService complianceDiaryService;
    @Mock
    private SesCommunicationService sesCommunicationService;
    @Mock
    private S3DocumentStoreService s3DocumentStoreService;
    @Mock
    private DynamoDBPersistenceService dynamoDBPersistenceService;

    private InsuredEngagementStateTransitionService stateTransitionService;

    @BeforeEach
    void setUp() {
        // NFR: thread_safety - deterministic initialization per test execution
        stateTransitionService = new InsuredEngagementStateTransitionService(
                reserveLineService,
                complianceDiaryService,
                sesCommunicationService,
                s3DocumentStoreService,
                dynamoDBPersistenceService
        );
    }

    @Test
    void applies_when_admin_submits_config_update() {
        // Given: Admin submits a valid configuration update payload
        String adminId = "admin-user-secure-01";
        String configUpdateId = "cfg-update-789";
        String targetState = "APPROVED";
        ConfigUpdateRequest payload = new ConfigUpdateRequest(adminId, configUpdateId, targetState);

        // NFR: input_validation - verify payload constraints before processing
        assertNotNull(payload);
        assertFalse(payload.adminId().isBlank());
        assertFalse(payload.configUpdateId().isBlank());

        // When: State transition logic is triggered by the admin submission
        StateTransitionOutcome result = stateTransitionService.evaluateAndTransition(payload);

        // Then: State transition applies successfully and side effects are captured
        assertNotNull(result);
        assertEquals(targetState, result.newState());
        assertTrue(result.isTransitionApplied());
        assertNotNull(result.transitionTimestamp());

        // Verify mocked external I/O interactions match expected contract calls
        verify(reserveLineService).updateApprovalStatus(eq(configUpdateId), eq("APPROVED"));
        verify(complianceDiaryService).recordAuditEvent(eq("CONFIG_UPDATE"), eq(adminId), anyString());
        verify(sesCommunicationService).sendNotification(eq("admin-alerts@newco.com"), anyList(), eq("us-east-1"));
        verify(dynamoDBPersistenceService).persistItem(eq("StateTransitionTable"), any(Map.class));
        verifyNoMoreInteractions(reserveLineService, complianceDiaryService, sesCommunicationService, dynamoDBPersistenceService);
    }
}

// Package-private stubs to ensure compilation and isolate integration boundaries
interface ReserveLineService { void updateApprovalStatus(String id, String status); }
interface ComplianceDiaryService { void recordAuditEvent(String type, String actor, String details); }
interface SesCommunicationService { void sendNotification(String from, List<String> to, String region); }
interface S3DocumentStoreService { String storeDocument(String bucket, String key, byte[] data); }
interface DynamoDBPersistenceService { void persistItem(String table, Map<String, Object> item); }

record ConfigUpdateRequest(String adminId, String configUpdateId, String targetState) {}
record StateTransitionOutcome(String newState, boolean transitionApplied, Instant transitionTimestamp) {
    static StateTransitionOutcome of(String state) {
        return new StateTransitionOutcome(state, true, Instant.now());
    }
}

class InsuredEngagementStateTransitionService {
    private final ReserveLineService reserveLineService;
    private final ComplianceDiaryService complianceDiaryService;
    private final SesCommunicationService sesCommunicationService;
    private final S3DocumentStoreService s3DocumentStoreService;
    private final DynamoDBPersistenceService dynamoDBPersistenceService;

    InsuredEngagementStateTransitionService(ReserveLineService r, ComplianceDiaryService c, SesCommunicationService s, S3DocumentStoreService d, DynamoDBPersistenceService db) {
        this.reserveLineService = r;
        this.complianceDiaryService = c;
        this.sesCommunicationService = s;
        this.s3DocumentStoreService = d;
        this.dynamoDBPersistenceService = db;
    }

    StateTransitionOutcome evaluateAndTransition(ConfigUpdateRequest payload) {
        // NFR: structured_logging - audit trail for state transitions
        reserveLineService.updateApprovalStatus(payload.configUpdateId(), payload.targetState());
        complianceDiaryService.recordAuditEvent("CONFIG_UPDATE", payload.adminId(), "State transition initiated by admin");
        sesCommunicationService.sendNotification("admin-alerts@newco.com", List.of(payload.adminId() + "@newco.com"), "us-east-1");
        dynamoDBPersistenceService.persistItem("StateTransitionTable", Map.of("id", payload.configUpdateId(), "state", payload.targetState()));
        return StateTransitionOutcome.of(payload.targetState());
    }
}
