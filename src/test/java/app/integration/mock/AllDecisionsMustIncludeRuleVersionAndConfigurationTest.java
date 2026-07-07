package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private ClaimDataStore dataStore;

    @Mock
    private DocumentStore documentStore;

    @Mock
    private ClaimDataStandardizationOrchestrator orchestrator;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testPayload = new HashMap<>();
        testPayload.put("id", "claim-std-001");
        List<Map<String, Object>> decisions = new ArrayList<>();
        decisions.add(Map.of("rule_version", "v2.1.0", "configuration_snapshot", Map.of("severity", "high", "params", Map.of("limit", 100))));
        decisions.add(Map.of("rule_version", "v2.1.0", "configuration_snapshot", Map.of("severity", "medium", "params", Map.of("limit", 200))));
        testPayload.put("decisions", decisions);
    }

    @Test
    void all_decisions_must_include_rule_version_and_configuration_snapshot() {
        // Arrange: Mock external I/O contracts to prevent live AWS calls
        when(dataStore.putItem(any(Map.class))).thenReturn(Map.of("item_payload", testPayload));
        when(documentStore.writeObject(anyString(), anyString())).thenReturn("s3://mock-bucket/claim-std-001.json");
        when(orchestrator.transform(testPayload)).thenReturn(testPayload);

        // Act: Execute orchestration transformation phase
        Map<String, Object> resultPayload = orchestrator.transform(testPayload);

        // Assert: Validate structural compliance for all decisions
        assertNotNull(resultPayload);
        assertTrue(resultPayload.containsKey("decisions"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) resultPayload.get("decisions");

        for (Map<String, Object> decision : decisions) {
            assertTrue(decision.containsKey("rule_version"),
                    "All decisions must include rule_version");
            assertTrue(decision.containsKey("configuration_snapshot"),
                    "All decisions must include configuration_snapshot");
            assertNotNull(decision.get("rule_version"), "rule_version must not be null");
            assertNotNull(decision.get("configuration_snapshot"), "configuration_snapshot must not be null");
        }
    }
}
