package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolOrchestrationValidationTest {

    private static final long MAX_RESOLUTION_MS = Duration.ofHours(24).toMillis();

    @Mock
    private ClaimIntakeService claimIntakeService;

    @Mock
    private CommunicationsHandler communicationsHandler;

    @Mock
    private DataStore dataStore;

    @Mock
    private Clock testClock;

    private FnolOrchestrationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FnolOrchestrationValidator(
                claimIntakeService,
                communicationsHandler,
                dataStore,
                testClock
        );
    }

    @Test
    void task_resolves_within_24_hours() {
        // Arrange
        String submissionId = "fnol-mc-001";
        Map<String, Object> payload = Map.of(
                "id", submissionId,
                "channel", "WEB",
                "status", "INITIATED"
        );

        Instant taskStart = Instant.parse("2024-01-01T10:00:00Z");
        Instant taskEnd = Instant.parse("2024-01-01T18:00:00Z"); // 8 hours later

        when(testClock.instant())
                .thenReturn(taskStart)
                .thenReturn(taskEnd);

        // Act
        Instant resolvedAt = validator.resolveTask(submissionId, payload);

        // Assert
        assertNotNull(resolvedAt, "Resolved timestamp must not be null");
        long elapsedMs = Duration.between(taskStart, resolvedAt).toMillis();
        assertTrue(elapsedMs <= MAX_RESOLUTION_MS,
                "Orchestration validation task must resolve within 24 hours. Actual: " + elapsedMs + "ms");

        verify(testClock, times(2)).instant();
        verify(dataStore).saveItem(eq("multi_channel_fnol_submission_state_transition_c"), any(Map.class));
        verify(claimIntakeService).storePayload(anyString(), anyString(), any(Map.class));
        verify(communicationsHandler).sendVerificationEmail(anyString(), anyList(), anyString());
    }
}

// Infra I/O Contract Mock Interfaces
interface ClaimIntakeService {
    String storePayload(String bucketName, String objectKeyPattern, Map<String, Object> payload);
}

interface CommunicationsHandler {
    String sendVerificationEmail(String fromAddress, List<String> toAddresses, String region);
}

interface DataStore {
    void saveItem(String tableName, Map<String, Object> itemPayload);
}

// Service Under Test
class FnolOrchestrationValidator {
    private final ClaimIntakeService claimIntakeService;
    private final CommunicationsHandler communicationsHandler;
    private final DataStore dataStore;
    private final Clock clock;

    FnolOrchestrationValidator(ClaimIntakeService claimIntakeService,
                               CommunicationsHandler communicationsHandler,
                               DataStore dataStore,
                               Clock clock) {
        this.claimIntakeService = claimIntakeService;
        this.communicationsHandler = communicationsHandler;
        this.dataStore = dataStore;
        this.clock = clock;
    }

    Instant resolveTask(String submissionId, Map<String, Object> payload) {
        Instant start = clock.instant();
        // Simulate multi-channel FNOL orchestration & validation pipeline
        claimIntakeService.storePayload("Claim Intake Service-bucket",
                "Claim Intake Service/" + submissionId + ".json", payload);
        dataStore.saveItem("multi_channel_fnol_submission_state_transition_c", payload);
        communicationsHandler.sendVerificationEmail("noreply@newcoinsurance.com",
                List.of("claimant@example.com"), "us-east-1");
        Instant end = clock.instant();
        return end;
    }
}
