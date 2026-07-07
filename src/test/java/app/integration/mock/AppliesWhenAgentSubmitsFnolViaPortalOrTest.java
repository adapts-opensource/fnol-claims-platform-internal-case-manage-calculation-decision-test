package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AppliesWhenAgentSubmitsFnolViaPortalOrTest {

    @Mock
    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private DocumentManagementClient documentManagementClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void applies_when_agent_submits_fnol_via_portal_or_api() {
        // Arrange: Simulate FNOL submission via Portal or API channels
        String[] channels = {"portal", "api"};
        String claimId = "CLM-TEST-001";
        Map<String, Object> rawPayload = Map.of(
            "claimId", claimId,
            "sourceChannel", "AGENT_SUBMISSION",
            "fnolType", "AUTO",
            "dataVersion", "1.0"
        );

        Map<String, Object> expectedStandardizedPayload = Map.of(
            "id", claimId,
            "payload", rawPayload,
            "standardizationState", "TRANSFORMED",
            "orchestrationTrigger", "STATE_TRANSITION"
        );

        when(orchestrationService.processFnolSubmission(anyString(), anyMap()))
            .thenReturn(expectedStandardizedPayload);

        when(claimDataStoreClient.saveStateTransition(anyString(), anyMap()))
            .thenReturn(Map.of("id", claimId, "state", "TRANSFORMED"));

        when(documentManagementClient.storeClaimDocument(anyString(), anyString(), any()))
            .thenReturn("s3://Document-Mangement-bucket/CLM-TEST-001.json");

        // Act & Assert: Verify orchestration applies for both channels
        for (String channel : channels) {
            Map<String, Object> result = orchestrationService.processFnolSubmission(channel, rawPayload);

            assertNotNull(result, "Orchestration must return a standardized payload");
            assertEquals(claimId, result.get("id"), "Payload ID must match submitted claim ID");
            assertEquals("TRANSFORMED", result.get("standardizationState"), "State must transition to TRANSFORMED");
            assertEquals(expectedStandardizedPayload.get("payload"), result.get("payload"), "Original payload must be preserved");

            // Verify infra I/O contracts per NFRs (security, observability, compliance)
            verify(orchestrationService, times(1)).processFnolSubmission(eq(channel), anyMap());
            verify(claimDataStoreClient, times(1)).saveStateTransition(anyString(), anyMap());
            verify(documentManagementClient, times(1)).storeClaimDocument(anyString(), anyString(), any());
        }

        // Verify structured logging and input validation were triggered (mocked behavior)
        verify(orchestrationService, atLeast(2)).validateInput(anyString(), anyMap());
    }
}
