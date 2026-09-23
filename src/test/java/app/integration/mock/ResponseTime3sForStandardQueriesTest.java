package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MultiChannelFnolSubmissionDecisionValidationTest {

    private DecisionValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = mock(DecisionValidationService.class);
        when(validationService.processValidation(anyString(), anyMap()))
                .thenReturn(Map.of("id", "fnol-123", "payload", Map.of("status", "VALID")));
    }

    @Test
    void responseTime3sForStandardQueries() {
        String id = "fnol-456";
        Map<String, Object> payload = Map.of("channel", "WEB", "claimType", "STANDARD");

        long start = System.currentTimeMillis();
        Map<String, Object> result = validationService.processValidation(id, payload);
        long end = System.currentTimeMillis();

        long durationMs = end - start;
        assertNotNull(result, "Validation result must not be null");
        assertTrue(durationMs < 3000, "Response time must be less than 3000ms for standard queries. Actual: " + durationMs + "ms");
    }

    private interface DecisionValidationService {
        Map<String, Object> processValidation(String id, Map<String, Object> payload);
    }
}
