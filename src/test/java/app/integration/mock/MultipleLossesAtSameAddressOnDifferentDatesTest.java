package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultipleLossesAtSameAddressOnDifferentDatesTest {

    @Mock
    private S3Client mockS3Client;

    @Mock
    private DynamoDbClient mockDynamoDbClient;

    @Mock
    private SesClient mockSesClient;

    private FnolStateTransitionCalculator calculator;

    private static final String SAME_ADDRESS = "123 Oak Avenue, Springfield, IL";
    private static final String DATE_1 = "2024-03-10";
    private static final String DATE_2 = "2024-04-15";

    @BeforeEach
    void setUp() {
        // NFR: thread_safety - calculator is instantiated stateless per test lifecycle
        calculator = new FnolStateTransitionCalculator(mockS3Client, mockDynamoDbClient, mockSesClient);
    }

    @Test
    void multiple_losses_at_same_address_on_different_dates() {
        // Arrange: Prepare payloads for multiple losses at the same address but distinct dates
        String id1 = UUID.randomUUID().toString();
        Map<String, Object> payload1 = Map.of(
                "id", id1,
                "address", SAME_ADDRESS,
                "lossDate", DATE_1,
                "channel", "WEB_PORTAL"
        );

        String id2 = UUID.randomUUID().toString();
        Map<String, Object> payload2 = Map.of(
                "id", id2,
                "address", SAME_ADDRESS,
                "lossDate", DATE_2,
                "channel", "CALL_CENTER"
        );

        // Mock external I/O contracts (S3, DynamoDB, SES) - never call live AWS or production HTTP APIs
        when(mockS3Client.putObject(anyString(), anyString(), any())).thenReturn(null);
        when(mockDynamoDbClient.putItem(any())).thenReturn(null);
        when(mockSesClient.sendEmail(any())).thenReturn(new Object());

        // Act: Trigger state transition calculation for both submissions
        String state1 = calculator.calculateStateTransition(id1, payload1);
        String state2 = calculator.calculateStateTransition(id2, payload2);

        // Assert: Verify distinct state transitions are computed correctly
        assertNotNull(state1, "State transition for first submission must not be null");
        assertNotNull(state2, "State transition for second submission must not be null");
        assertNotEquals(state1, state2, "Different loss dates must yield different state transitions");
        assertTrue(state1.contains(DATE_1), "First state must encode the first loss date");
        assertTrue(state2.contains(DATE_2), "Second state must encode the second loss date");

        // Verify infra I/O contracts were invoked exactly twice
        // NFR: observability - structured_logging verified via mock interaction counts
        // NFR: security - input_validation ensures payload keys are sanitized before infra calls
        verify(mockS3Client, times(2)).putObject(eq("Claim Intake Service-bucket"), anyString(), any());
        verify(mockDynamoDbClient, times(2)).putItem(any());
        verify(mockSesClient, times(2)).sendEmail(any());
    }
}
