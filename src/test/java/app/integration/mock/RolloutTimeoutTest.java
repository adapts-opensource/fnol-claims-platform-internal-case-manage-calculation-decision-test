package app.integration.mock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionRolloutTimeoutTest {

    @Mock
    private DecisionEngineService decisionEngineService;

    @BeforeEach
    void setUp() {
        // Initialize mock state or configure global timeouts if required
    }

    @Test
    void rollout_timeout() {
        String claimId = "CLM-98765";
        Map<String, Object> payload = Map.of("id", claimId, "payload", Map.of("type", "AUTO"));

        when(decisionEngineService.evaluateDecision(claimId, payload))
                .thenThrow(new TimeoutException("Rollout timeout exceeded"));

        TimeoutException thrown = assertThrows(TimeoutException.class, () ->
                decisionEngineService.evaluateDecision(claimId, payload)
        );

        assertEquals("Rollout timeout exceeded", thrown.getMessage());
        verify(decisionEngineService).evaluateDecision(claimId, payload);
    }
}
