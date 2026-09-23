package app.integration.mock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
// JUnit 5 test class with @Test methods
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationOrchestrationEndDateFormatTest {

    @Mock
    private ClaimOrchestrationService orchestrationService;

    @Mock
    private Logger structuredLogger;

    private ClaimDataStandardizationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimDataStandardizationOrchestrator(orchestrationService, structuredLogger);
    }

    @Test
    void end_date() {
        // Given: Input payload containing raw end_date string
        String claimId = "CLM-STD-001";
        Map<String, Object> inputPayload = Map.of("end_date", "2024-06-30");

        // Mock orchestration to simulate transformation & persistence layer
        Map<String, Object> transformedPayload = Map.of(
            "id", claimId,
            "payload", Map.of("end_date", "2024-06-30T00:00:00Z", "status", "STANDARDIZED")
        );
        when(orchestrationService.execute(eq(claimId), anyMap())).thenReturn(transformedPayload);

        // When: Orchestration runs with input payload
        Map<String, Object> result = orchestrator.processClaim(claimId, inputPayload);

        // Then: Verify transformation applied, end_date normalized to ISO-8601, and services invoked
        assertNotNull(result, "Orchestration result must not be null");
        assertEquals(transformedPayload, result, "Payload should match transformed output");
        assertEquals("2024-06-30T00:00:00Z", result.get("end_date"), "end_date must be normalized to ISO-8601");
        verify(structuredLogger).info(eq("Claim data standardized successfully"), eq(claimId));
        verify(orchestrationService).execute(eq(claimId), anyMap());
    }
}
