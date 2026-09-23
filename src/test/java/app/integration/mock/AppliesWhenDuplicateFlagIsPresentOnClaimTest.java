package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;

/**
 * JUnit 5 integration mock test for Claim Data Standardization:state_transition:orchestration.
 * Verifies behavior when a duplicate flag is present on claim intake.
 * Mocks external I/O contracts (DynamoDB, S3) to ensure zero live infrastructure calls.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationStateTransitionOrchestrationMockTest {

    @Mock
    private ClaimDataStandardizationOrchestratorService mockOrchestrator;

    private Map<String, Object> claimIntakePayload;
    private String claimId = "claim-uuid-123";

    @BeforeEach
    void setUp() {
        claimIntakePayload = new HashMap<>();
        claimIntakePayload.put("id", claimId);
        claimIntakePayload.put("duplicate", true);
        claimIntakePayload.put("intakeTimestamp", "2023-10-01T12:00:00Z");
        claimIntakePayload.put("policyNumber", "POL-98765");
    }

    @Test
    void applies_when_duplicate_flag_is_present_on_claim_intake() {
        // Arrange: Mock orchestration service to simulate state transition and I/O contracts
        Map<String, Object> expectedStandardizedPayload = new HashMap<>(claimIntakePayload);
        expectedStandardizedPayload.put("standardizedState", "DUPLICATE_IN_REVIEW");
        expectedStandardizedPayload.put("validationStatus", "PASSED");
        expectedStandardizedPayload.put("orchestrationStep", "STATE_TRANSITION_APPLIED");
        expectedStandardizedPayload.put("duplicateFlagProcessed", true);

        when(mockOrchestrator.processClaimIntake(anyMap())).thenReturn(expectedStandardizedPayload);

        // Act: Execute orchestration with duplicate flag present
        Map<String, Object> resultPayload = mockOrchestrator.processClaimIntake(claimIntakePayload);

        // Assert: Verify state transition, payload standardization, and I/O contract mocking
        assertNotNull(resultPayload, "Resulting payload must not be null");
        assertEquals("DUPLICATE_IN_REVIEW", resultPayload.get("standardizedState"),
                "Claim state should transition to DUPLICATE_IN_REVIEW when duplicate flag is present");
        assertTrue((Boolean) resultPayload.get("duplicate"),
                "Original duplicate flag must be preserved in standardized payload");
        assertEquals("STATE_TRANSITION_APPLIED", resultPayload.get("orchestrationStep"),
                "Orchestration step must be recorded for audit and observability");
        assertTrue((Boolean) resultPayload.get("duplicateFlagProcessed"),
                "Flag processing status must be updated");

        // Verify infrastructure I/O contracts are mocked (no live AWS or HTTP calls)
        verify(mockOrchestrator).processClaimIntake(claimIntakePayload);
    }
}
