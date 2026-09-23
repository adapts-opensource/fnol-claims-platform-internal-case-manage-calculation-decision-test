package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionOrchestrationValidationMockTest {

    @Mock
    private FnolSubmissionValidator submissionValidator;

    @Mock
    private StateTransitionCalculator stateTransitionCalculator;

    private MultiChannelFnolOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new MultiChannelFnolOrchestrationService(submissionValidator, stateTransitionCalculator);
    }

    @Test
    void communication_preferences_override_system_defaults() {
        // Given
        String submissionId = "fnol-uuid-123";
        Map<String, Object> systemDefaults = Map.of("communicationChannel", "EMAIL", "notificationFrequency", "IMMEDIATE");
        Map<String, Object> channelOverrides = Map.of("communicationChannel", "SMS", "notificationFrequency", "DAILY");
        Map<String, Object> expectedPayload = Map.of("communicationPreferences", channelOverrides);

        when(submissionValidator.validate(anyMap())).thenReturn(true);
        when(stateTransitionCalculator.calculate(anyMap(), anyMap())).thenReturn(expectedPayload);

        // When
        Map<String, Object> result = orchestrationService.processSubmission(submissionId, channelOverrides);

        // Then
        assertNotNull(result);
        assertEquals(submissionId, result.get("id"));
        Map<String, Object> payload = (Map<String, Object>) result.get("payload");
        assertNotNull(payload);
        assertEquals(channelOverrides, payload.get("communicationPreferences"));
        assertEquals("SMS", payload.get("communicationPreferences").get("communicationChannel"));
        assertEquals("DAILY", payload.get("communicationPreferences").get("notificationFrequency"));

        verify(submissionValidator).validate(anyMap());
        verify(stateTransitionCalculator).calculate(anyMap(), eq(channelOverrides));
    }
}
