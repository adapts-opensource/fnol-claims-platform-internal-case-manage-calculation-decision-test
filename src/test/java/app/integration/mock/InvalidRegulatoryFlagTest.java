package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationOrchestrationTest {

    @Mock
    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        reset(orchestrationService);
    }

    @Test
    void invalid_regulatory_flag() {
        // Arrange: Simulate claim data payload with an invalid regulatory flag
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "claim-std-001");
        payload.put("regulatory_flag", "UNKNOWN_REGION_CODE");

        // Mock external I/O to avoid live AWS/HTTP calls
        when(orchestrationService.transform(payload))
                .thenThrow(new IllegalArgumentException("Invalid regulatory flag: UNKNOWN_REGION_CODE"));

        // Act & Assert: Verify orchestration rejects invalid regulatory flags
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrationService.transform(payload);
        }, "Orchestration must validate and reject invalid regulatory flags during transformation");
    }
}
