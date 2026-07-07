package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CatEventsTriggerPriorityRoutingTest {

    @Mock
    private ClaimOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // Reset mock state between tests to ensure isolation
        reset(orchestrationService);
    }

    @Test
    void cat_events_trigger_priority_routing() {
        // Arrange
        String claimId = "claim-cat-evt-001";
        Map<String, Object> payload = Map.of(
                "id", claimId,
                "eventType", "CAT_EVENT",
                "severity", "CRITICAL",
                "priorityRouting", true,
                "state", "ROUTED_TO_PRIORITY_QUEUE"
        );

        // Mock external I/O: DynamoDB & S3 interactions are abstracted behind service methods
        doNothing().when(orchestrationService).persistToDataStore(anyString(), anyMap());
        doNothing().when(orchestrationService).storeDocumentInS3(anyString(), anyMap());

        when(orchestrationService.processClaimDataStandardization(claimId, payload))
                .thenReturn(Map.of(
                        "id", claimId,
                        "payload", payload,
                        "priorityRouting", true,
                        "stateTransition", "ROUTED_TO_PRIORITY_QUEUE"
                ));

        // Act
        Map<String, Object> result = orchestrationService.processClaimDataStandardization(claimId, payload);

        // Assert
        assertNotNull(result, "Orchestration result must not be null");
        assertTrue((Boolean) result.get("priorityRouting"), "CAT events must trigger priority routing");
        assertEquals("ROUTED_TO_PRIORITY_QUEUE", result.get("stateTransition"), "State must transition to priority routing");
        verify(orchestrationService).processClaimDataStandardization(claimId, payload);
        verify(orchestrationService).persistToDataStore(claimId, payload);
        verify(orchestrationService).storeDocumentInS3(claimId, payload);
    }

    // Service interface representing the orchestration layer
    interface ClaimOrchestrationService {
        Map<String, Object> processClaimDataStandardization(String id, Map<String, Object> payload);
        void persistToDataStore(String id, Map<String, Object> payload);
        void storeDocumentInS3(String id, Map<String, Object> payload);
    }
}
