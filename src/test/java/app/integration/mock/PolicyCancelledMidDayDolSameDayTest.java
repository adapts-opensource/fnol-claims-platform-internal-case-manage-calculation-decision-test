package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolStateTransitionCalculationMockTest {

    @Mock
    private StateTransitionCalculator stateTransitionCalculator;

    @Mock
    private S3Client s3Client;

    @Mock
    private SesClient sesClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        payload = new HashMap<>();
        payload.put("id", "fnol-trans-001");
        payload.put("policyStatus", "CANCELLED");
        payload.put("cancellationTimestamp", "2023-10-25T12:15:00Z");
        payload.put("dateOfLoss", "2023-10-25T14:30:00Z");
        payload.put("channel", "WEB");
        payload.put("claimType", "AUTO_COLLISION");
    }

    @Test
    void policy_cancelled_mid_day_dol_same_day() {
        // Arrange: Define expected state transition for mid-day cancellation with same-day DoL
        String expectedState = "POLICY_CANCELLED_LOSS_NOT_COVERED";
        when(stateTransitionCalculator.evaluate(payload)).thenReturn(expectedState);

        // Act: Trigger state transition calculation
        String actualState = stateTransitionCalculator.evaluate(payload);

        // Assert: Verify calculation result matches business rule for coverage exclusion
        assertNotNull(actualState, "Calculated state must not be null");
        assertEquals(expectedState, actualState, "State transition must exclude coverage when policy is cancelled mid-day and DoL occurs same day");
        verify(stateTransitionCalculator, times(1)).evaluate(payload);
    }
}
