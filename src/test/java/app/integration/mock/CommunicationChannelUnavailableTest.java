package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;

/**
 * JUnit 5 test class with @Test methods
 * Verifies that claim initiation and routing decision calculation gracefully handles
 * scenarios where the communication channel is unavailable.
 */
public class CommunicationChannelUnavailableTest {

    @Mock
    private CommunicationChannelService communicationChannelService;

    private ClaimDecisionCalculator decisionCalculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        decisionCalculator = new ClaimDecisionCalculator(communicationChannelService);
    }

    @Test
    void communication_channel_unavailable() {
        // Given: Communication channel is unavailable
        doThrow(new CommunicationChannelUnavailableException("Channel service unreachable"))
            .when(communicationChannelService).notifyRoutingDecision(anyString(), any(Map.class));

        // When & Then: System should fail gracefully with a domain-specific exception
        assertThrows(CommunicationChannelUnavailableException.class, () -> {
            decisionCalculator.calculateRoutingDecision("claim-initiation-id", Map.of("channel", "email"));
        });
    }
}
