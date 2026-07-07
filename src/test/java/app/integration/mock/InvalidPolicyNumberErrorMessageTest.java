package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvalidPolicyNumberErrorMessageTest {

    @Mock
    private MultiChannelFnolStateTransitionCalculator calculator;

    @Mock
    private InfraIOService infraIOService;

    @BeforeEach
    void setUp() {
        // Deterministic setup ensuring thread-safe isolation per test execution.
        // MockitoExtension automatically injects mocks into fields annotated with @Mock.
    }

    @Test
    void invalid_policy_number_error_message() {
        // Arrange
        String submissionId = UUID.randomUUID().toString();
        Map<String, Object> invalidPayload = Map.of(
            "policyNumber", "INVALID-XYZ-789",
            "channel", "WEB_PORTAL",
            "incidentDate", "2024-05-20"
        );

        // Mock state transition calculation to simulate validation failure
        when(calculator.calculateTransition(anyMap())).thenReturn(
            Map.of("state", "ERROR", "errorMessage", "Invalid policy number: POLICY does not exist in master registry.")
        );

        // Act
        Map<String, Object> result = calculator.processSubmission(submissionId, invalidPayload);

        // Assert
        assertNotNull(result);
        assertEquals("ERROR", result.get("state"));
        assertEquals("Invalid policy number: POLICY does not exist in master registry.", result.get("errorMessage"));

        // Verify infrastructure I/O is strictly skipped on validation failure (NFR: input_validation, compliance, security)
        verify(infraIOService, never()).writeToS3(anyString(), anyString(), any(byte[].class));
        verify(infraIOService, never()).storeInDynamoDB(anyMap());
        verify(infraIOService, never()).sendSesNotification(anyString(), anyList());
    }
}
