package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IfMultipleMatchesExistCreateResolvePolicyMatch {

    @Mock
    private ClaimDataStandardizationOrchestrator orchestrator;
    @Mock
    private PolicyMatchDetectionService matchDetectionService;
    @Mock
    private TaskCreationService taskCreationService;

    private String claimId;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        claimId = "CLM-2024-001";
        payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("matches", List.of("POL-101", "POL-102", "POL-103"));
        payload.put("standardizationStatus", "MULTIPLE_POLICIES_FOUND");
    }

    @Test
    void if_multiple_matches_exist_create_resolve_policy_match_task() {
        // Arrange
        when(matchDetectionService.detectMatches(payload)).thenReturn((List<String>) payload.get("matches"));
        when(matchDetectionService.hasMultipleMatches(payload)).thenReturn(true);

        // Act
        orchestrator.executeStandardizationFlow(claimId, payload);

        // Assert
        ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> taskPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(taskCreationService, times(1))
            .createTask(eq("RESOLVE_POLICY_MATCH"), taskIdCaptor.capture(), taskPayloadCaptor.capture());

        assertEquals(claimId, taskIdCaptor.getValue());
        assertTrue(taskPayloadCaptor.getValue().containsKey("matches"));
        assertEquals(3, ((List<?>) taskPayloadCaptor.getValue().get("matches")).size());
        verifyNoMoreInteractions(taskCreationService);
    }
}
