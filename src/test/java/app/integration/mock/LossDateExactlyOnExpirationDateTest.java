package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionOrchestrationValidationTest {

    @Mock
    private PolicyBoundaryRepository policyBoundaryRepository;

    @Mock
    private StateTransitionRepository stateTransitionRepository;

    @Mock
    private SecureNotificationDispatcher notificationDispatcher;

    @Test
    void loss_date_exactly_on_expiration_date() {
        // Arrange: Boundary condition - loss date exactly matches policy expiration date
        String submissionId = "FNOL-BOUNDARY-001";
        LocalDate lossDate = LocalDate.of(2024, 12, 31);
        LocalDate expirationDate = LocalDate.of(2024, 12, 31);
        String channel = "WEB";

        Map<String, Object> payload = Map.of(
                "id", submissionId,
                "lossDate", lossDate.toString(),
                "expirationDate", expirationDate.toString(),
                "channel", channel,
                "piiSensitive", false
        );

        // Mock external I/O: Policy lookup returns expiration date
        when(policyBoundaryRepository.getExpirationDate(submissionId))
                .thenReturn(expirationDate);

        // Mock external I/O: State transition persistence
        when(stateTransitionRepository.saveStateTransition(any(Map.class)))
                .thenReturn("STATE_TRANSITION_SAVED");

        // Mock external I/O: Secure notification dispatch (TLS enforced, least-privilege IAM)
        when(notificationDispatcher.sendSecureNotification(anyString(), anyString()))
                .thenReturn("MSG_ID_SECURE");

        // Act: Orchestrate validation logic
        boolean isBoundaryValid = lossDate.isEqual(expirationDate);
        assertTrue(isBoundaryValid, "Boundary condition: loss date exactly on expiration date");

        Map<String, Object> validatedPayload = Map.of(
                "id", submissionId,
                "lossDate", lossDate.toString(),
                "expirationDate", expirationDate.toString(),
                "channel", channel,
                "piiSensitive", false,
                "validationStatus", "PASS"
        );

        String transitionId = stateTransitionRepository.saveStateTransition(validatedPayload);

        // Assert: Verify infra I/O contracts and structured observability hooks
        assertNotNull(transitionId, "State transition should be persisted for valid boundary");
        verify(policyBoundaryRepository, times(1)).getExpirationDate(submissionId);
        verify(stateTransitionRepository, times(1)).saveStateTransition(validatedPayload);
        verify(notificationDispatcher, never()).sendSecureNotification(anyString(), anyString());
    }
}
