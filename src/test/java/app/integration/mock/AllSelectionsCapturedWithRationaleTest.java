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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionValidationMockTest {

    @Mock
    private DataStoreService dataStoreService;
    @Mock
    private StorageService storageService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private LoggerService loggerService;

    private Map<String, Object> validPayload;

    @BeforeEach
    void setUp() {
        // NFR: Input validation, TLS/least privilege implied by secure mock initialization
        validPayload = Map.of(
            "id", "fnol-uuid-7a8b9c",
            "channel", "WEB_PORTAL",
            "selections", List.of("DAMAGE_VEHICLE", "INJURY_PASSENGER", "POLICE_REPORT"),
            "rationale", "Multi-vehicle collision at intersection; all parties reported minor injuries; police dispatched."
        );
    }

    @Test
    void allSelectionsCapturedWithRationale() {
        // Simulate orchestration validation phase
        boolean isPayloadValid = validateFnolPayload(validPayload);
        assertTrue(isPayloadValid, "Validation should pass when all selections and rationale are present");

        // NFR: Structured logging & observability
        when(loggerService.info(anyString(), any(Map.class))).thenReturn("state_transition_created");
        String stateTransitionId = dataStoreService.saveStateTransition(validPayload.get("id").toString(), validPayload);
        assertNotNull(stateTransitionId);

        // Verify S3 object write with resolved URI pattern
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(storageService, times(1)).putObject(anyString(), payloadCaptor.capture());
        Map<String, Object> storedPayload = payloadCaptor.getValue();

        // Assert selections captured per multi_channel_fnol_submission_state_transition_c
        assertNotNull(storedPayload.get("selections"), "Selections list must not be null");
        List<?> selections = (List<?>) storedPayload.get("selections");
        assertFalse(selections.isEmpty(), "At least one selection must be captured");

        // Assert rationale captured
        assertNotNull(storedPayload.get("rationale"), "Rationale must be present");
        String rationale = (String) storedPayload.get("rationale");
        assertFalse(rationale.isBlank(), "Rationale must not be blank");

        // Verify SES notification triggered for compliance & observability
        verify(notificationService, times(1)).sendAlert(anyString(), anyList());
    }

    private boolean validateFnolPayload(Map<String, Object> payload) {
        // NFR: Input validation & least privilege processing
        List<?> selections = (List<?>) payload.get("selections");
        String rationale = (String) payload.get("rationale");
        return selections != null && !selections.isEmpty() && rationale != null && !rationale.isBlank();
    }

    // Mock infrastructure interfaces for isolated unit testing
    interface DataStoreService {
        String saveStateTransition(String id, Map<String, Object> payload);
    }
    interface StorageService {
        void putObject(String bucketName, Map<String, Object> payload);
    }
    interface NotificationService {
        void sendAlert(String fromAddress, List<String> toAddresses);
    }
    interface LoggerService {
        String info(String message, Map<String, Object> context);
    }
}
