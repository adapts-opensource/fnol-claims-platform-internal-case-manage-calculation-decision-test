package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Insured Engagement & Tracking orchestration decision logic.
 * Validates engagement decisions based on policy status and reinstatement history.
 */
@ExtendWith(MockitoExtension.class)
class InsuredEngagementOrchestrationDecisionTest {

    @Mock
    private PolicyService policyService;

    @Mock
    private ReinstatementService reinstatementService;

    private InsuredEngagementOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new InsuredEngagementOrchestrator(policyService, reinstatementService);
    }

    @Test
    @DisplayName("policy_canceled_30_days_prior_with_no_reinstatement")
    void policyCanceled30DaysPriorWithNoReinstatement() {
        // Arrange
        String policyId = "POL-12345";
        LocalDate cancellationDate = LocalDate.now().minusDays(30);
        LocalDate evaluationDate = LocalDate.now();

        when(policyService.getStatus(policyId)).thenReturn(PolicyStatus.CANCELLED);
        when(policyService.getCancellationDate(policyId)).thenReturn(cancellationDate);
        when(reinstatementService.hasReinstatement(policyId)).thenReturn(false);

        // Act
        DecisionResult result = orchestrator.evaluate(policyId, evaluationDate);

        // Assert
        assertNotNull(result, "Decision result should not be null");
        assertEquals(DecisionOutcome.NO_ACTION, result.getOutcome(), "Outcome should be NO_ACTION for canceled policy without reinstatement");
        assertTrue(result.isEngagementBlocked(), "Engagement should be blocked");
        assertEquals(policyId, result.getPolicyId(), "Policy ID should match input");
        assertEquals("Policy canceled 30 days prior with no reinstatement", result.getReason(), "Reason should reflect cancellation and lack of reinstatement");
        verify(policyService, times(1)).getStatus(policyId);
        verify(policyService, times(1)).getCancellationDate(policyId);
        verify(reinstatementService, times(1)).hasReinstatement(policyId);
    }
}
