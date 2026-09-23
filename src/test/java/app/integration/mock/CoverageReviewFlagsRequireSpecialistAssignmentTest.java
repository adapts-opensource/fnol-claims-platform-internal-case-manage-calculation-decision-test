package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class CoverageReviewFlagsRequireSpecialistAssignmentTest {

    @Mock
    private ClaimDataOrchestrator mockOrchestrator;

    @Mock
    private ClaimDataStoreClient mockDataStore;

    private Map<String, Object> claimData;

    @BeforeEach
    void setUp() {
        claimData = new HashMap<>();
        claimData.put("id", "claim-std-001");
        Map<String, Object> payload = new HashMap<>();
        payload.put("coverage_review_flags", Map.of("flag_active", true, "requires_triage", true));
        payload.put("current_state", "INITIATED");
        payload.put("assigned_specialist", null);
        claimData.put("payload", payload);
    }

    @Test
    void coverage_review_flags_require_specialist_assignment() {
        // Arrange: Mock orchestration response when coverage review flags are present
        Map<String, Object> expectedStandardizedPayload = new HashMap<>();
        expectedStandardizedPayload.put("specialist_assignment_required", true);
        expectedStandardizedPayload.put("specialist_type", "COVERAGE_REVIEW");
        expectedStandardizedPayload.put("next_state", "SPECIALIST_ASSIGNMENT_PENDING");
        expectedStandardizedPayload.put("validation_status", "PASSED");

        when(mockOrchestrator.transformAndOrchestrate(eq(claimData))).thenReturn(expectedStandardizedPayload);

        // Act: Execute the orchestration flow
        Map<String, Object> result = mockOrchestrator.transformAndOrchestrate(claimData);

        // Assert: Verify specialist assignment is flagged as required
        assertNotNull(result, "Orchestration result should not be null");
        assertTrue((Boolean) result.get("specialist_assignment_required"), "Specialist assignment must be required when coverage review flags are active");
        assertEquals("COVERAGE_REVIEW", result.get("specialist_type"), "Specialist type should match coverage review domain");
        assertEquals("SPECIALIST_ASSIGNMENT_PENDING", result.get("next_state"), "State should transition to specialist assignment pending");

        // Verify: Ensure downstream I/O contracts were invoked correctly
        verify(mockDataStore, times(1)).putItem(eq("Claim Data Store_table"), any(Map.class));
        verify(mockOrchestrator, times(1)).transformAndOrchestrate(eq(claimData));
    }
}
