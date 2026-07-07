package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration mock test for Multi-Channel FNOL Submission orchestration validation.
 * Verifies detection of endorsement coverage gaps and correct state transition.
 */
@ExtendWith(MockitoExtension.class)
class EndorsementCoverageGapTest {

    @Mock
    private ValidationService validationService;

    @Mock
    private StateTransitionService stateTransitionService;

    @Mock
    private DataStoreService dataStoreService;

    @InjectMocks
    private FnolOrchestrationService fnolOrchestrationService;

    @Test
    void endorsement_coverage_gap() {
        // Arrange: Construct payload simulating an endorsement that creates a coverage gap
        // Loss is COLLISION, but endorsement removes COLLISION or effective date is after loss
        Map<String, Object> payload = new HashMap<>();
        payload.put("channel", "WEB");
        payload.put("policyId", "POL-END-001");
        payload.put("endorsement", Map.of(
            "type", "COVERAGE_REMOVAL",
            "removedCoverages", List.of("COLLISION"),
            "effectiveDate", "2024-12-31"
        ));
        payload.put("loss", Map.of(
            "date", "2024-06-15",
            "type", "COLLISION",
            "description", "Rear-end collision damage"
        ));
        payload.put("submissionId", "FNOL-2024-999");

        // Mock validation engine to detect the gap
        when(validationService.validate(anyMap())).thenReturn(
            ValidationResult.builder()
                .status(ValidationStatus.REJECTED)
                .reason("COVERAGE_GAP")
                .message("Loss type COLLISION not covered due to endorsement effective date or removal.")
                .build()
        );

        // Act: Execute orchestration
        Map<String, Object> result = fnolOrchestrationService.processSubmission("FNOL-2024-999", payload);

        // Assert: Verify validation result and state transition
        assertEquals(ValidationStatus.REJECTED, result.get("validationStatus"));
        assertEquals("COVERAGE_GAP", result.get("validationReason"));
        assertNotNull(result.get("errorMessage"));

        // Verify state transition to gap state
        verify(stateTransitionService).transition(
            eq("FNOL-2024-999"),
            eq(State.COVERAGE_GAP_DETECTED),
            anyMap()
        );

        // Verify data store interaction with gap payload
        verify(dataStoreService).saveItem(
            eq("Data Store_table"),
            eq("pk"),
            eq("FNOL-2024-999"),
            anyMap()
        );

        // Ensure no communication is sent for a rejected/gap submission
        verifyNoInteractions(any(CommunicationService.class));
        verifyNoMoreInteractions(validationService);
    }
}
