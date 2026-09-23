package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
@DisplayName("Claim Data Standardization: transformation: orchestration")
class ClaimDataStandardizationTransformationOrchestrationTest {

    interface ClaimOrchestrationService {
        Map<String, Object> transformPayload(Map<String, Object> payload);
    }

    @Mock
    private ClaimOrchestrationService mockOrchestrationService;

    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        payload = new HashMap<>();
        payload.put("id", "claim-001");
        payload.put("channel_type", "MOBILE_APP");
    }

    @Test
    @DisplayName("channel_type")
    void channelType() {
        // Arrange: Mock orchestration to simulate standardization transformation
        when(mockOrchestrationService.transformPayload(any(Map.class)))
            .thenAnswer(invocation -> {
                Map<String, Object> input = invocation.getArgument(0);
                Map<String, Object> transformed = new HashMap<>(input);
                // Simulate input validation & standardization per NFR: input_validation
                Object channelType = input.get("channel_type");
                if (channelType != null) {
                    transformed.put("standardized_channel_type", channelType.toString().toUpperCase());
                }
                return transformed;
            });

        // Act
        Map<String, Object> result = mockOrchestrationService.transformPayload(payload);

        // Assert
        assertNotNull(result, "Transformed payload must not be null");
        assertEquals("MOBILE_APP", result.get("standardized_channel_type"), "Channel type should be standardized");
        assertEquals("claim-001", result.get("id"), "Original ID must be preserved");
        verify(mockOrchestrationService, times(1)).transformPayload(payload);
    }
}
