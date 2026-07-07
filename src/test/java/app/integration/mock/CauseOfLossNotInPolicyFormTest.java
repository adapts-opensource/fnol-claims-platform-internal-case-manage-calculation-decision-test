package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock tests for Multi-Channel FNOL Submission orchestration validation.
 * Verifies validation rules without calling live infrastructure.
 */
@DisplayName("Multi-Channel FNOL Submission: Orchestration: Validation")
class MultiChannelFnolSubmissionValidationMockTest {

    @Mock
    private PolicyFormValidationService policyFormValidationService;

    @Mock
    private FnolOrchestrationService fnolOrchestrationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Cause of loss not in policy form")
    void causeOfLossNotInPolicyForm() {
        // Arrange
        String causeOfLoss = "FLOOD";
        String policyForm = "HO-3";
        String submissionId = "fnol-sub-001";

        // Mock validation service to return invalid for this specific combination
        when(policyFormValidationService.isCauseOfLossValidForPolicyForm(causeOfLoss, policyForm))
                .thenReturn(false);

        // Act & Assert
        // Expect validation to fail with specific error
        assertThrows(ValidationException.class, () -> {
            fnolOrchestrationService.validateSubmission(submissionId, causeOfLoss, policyForm);
        });

        // Verify interaction
        verify(policyFormValidationService, times(1))
                .isCauseOfLossValidForPolicyForm(causeOfLoss, policyForm);
    }
}
