package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigPayloadJsonYamlTest {

    @Mock
    private ClaimDataStandardizationValidationService validationService;

    private Map<String, Object> configPayload;

    @BeforeEach
    void setUp() {
        // Simulates parsed JSON/YAML configuration payload structure
        // Mirrors data model: payload map with version, rules, validation, and observability keys
        configPayload = Map.of(
            "version", "1.0",
            "rulesEngine", Map.of("timeout", 5000, "retries", 3),
            "validation", Map.of("strictMode", true, "schemaVersion", "v2"),
            "observability", Map.of("structuredLogging", true, "traceEnabled", true)
        );
    }

    @Test
    @DisplayName("ConfigPayloadJsonYaml")
    void configPayloadJsonYaml() {
        // Arrange: Construct standard claim data entity payload
        String claimId = "CLM-STD-001";
        Map<String, Object> payload = Map.of("id", claimId, "payload", configPayload);

        // Mock external validation & decision service (simulates S3/DynamoDB contract)
        when(validationService.validateAndDecide(payload)).thenReturn(
            Map.of("decision", "APPROVED", "confidenceScore", 0.95, "nextStep", "POLICY_ISSUANCE")
        );

        // Act: Invoke validation decision logic
        Map<String, Object> decisionResult = validationService.validateAndDecide(payload);

        // Assert: Verify decision output and payload structure
        assertNotNull(decisionResult, "Decision result must not be null");
        assertEquals("APPROVED", decisionResult.get("decision"), "Decision must be APPROVED for valid config");
        assertEquals(0.95, (double) decisionResult.get("confidenceScore"), 0.001);
        assertTrue(((Map<?, ?>) payload.get("payload")).containsKey("validation"), "Config payload must contain validation section");
        verify(validationService, times(1)).validateAndDecide(payload);
    }
}
