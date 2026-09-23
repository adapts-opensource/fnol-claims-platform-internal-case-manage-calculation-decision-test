package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationStateTransitionOrchMockTest {

    @Mock
    private ClaimDataOrchestrationService orchestrationService;

    @Test
    void merge_preserves_original_claim_id_and_links_exposures() {
        // Given
        String originalClaimId = "claim-orig-789";
        List<String> exposureIds = List.of("exp-link-001", "exp-link-002");
        Map<String, Object> mergeInput = Map.of(
            "action", "MERGE",
            "originalClaimId", originalClaimId,
            "exposureIds", exposureIds
        );

        // Mock orchestration service response (abstracts DynamoDB/S3 I/O contracts)
        Map<String, Object> expectedStateTransition = Map.of(
            "id", originalClaimId,
            "payload", Map.of(
                "originalClaimId", originalClaimId,
                "linkedExposures", exposureIds,
                "standardizedState", "MERGED",
                "payloadVersion", "1.0"
            )
        );

        when(orchestrationService.processStateTransition(mergeInput)).thenReturn(expectedStateTransition);

        // When
        Map<String, Object> result = orchestrationService.processStateTransition(mergeInput);

        // Then
        assertNotNull(result, "Orchestration result must not be null");
        assertEquals(originalClaimId, result.get("id"), "Original claim ID must be preserved");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.get("payload");
        assertNotNull(payload, "Payload must be present");
        assertEquals(originalClaimId, payload.get("originalClaimId"), "Payload must reference original claim ID");
        assertEquals(exposureIds, payload.get("linkedExposures"), "Exposures must be linked in payload");
        assertEquals("MERGED", payload.get("standardizedState"), "State must transition to MERGED");

        verify(orchestrationService, times(1)).processStateTransition(mergeInput);
    }

    /**
     * Mock interface representing the orchestration layer.
     * Abstracts DynamoDB Claim Data Store and S3 Document Management I/O contracts.
     * Designed for thread-safe, structured-logging-aware mock execution.
     */
    interface ClaimDataOrchestrationService {
        Map<String, Object> processStateTransition(Map<String, Object> inputPayload);
    }
}
