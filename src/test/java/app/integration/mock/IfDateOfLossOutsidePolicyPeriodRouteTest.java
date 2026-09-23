package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Mock integration tests for Insured Engagement & Tracking orchestration decision logic.
 * Verifies routing behavior based on policy period validation.
 */
@ExtendWith(MockitoExtension.class)
class InsuredEngagementOrchestrationDecisionTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private PolicyService policyService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private InsuredEngagementOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // Reset mocks between tests if necessary, MockitoExtension handles injection.
    }

    @Test
    void if_date_of_loss_outside_policy_period_route_to_coverage_review() {
        // Arrange
        String claimId = "CLM-DOS-OUTSIDE-001";
        LocalDate dateOfLoss = LocalDate.of(2018, 6, 15);
        LocalDate policyStartDate = LocalDate.of(2019, 1, 1);
        LocalDate policyEndDate = LocalDate.of(2019, 12, 31);

        Claim mockClaim = new Claim(claimId, "INC-001", "EXP-001", dateOfLoss);
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(mockClaim));

        PolicyValidationResult validation = new PolicyValidationResult(false, "Date of loss is outside policy period");
        when(policyService.validatePolicyPeriod(claimId, dateOfLoss)).thenReturn(validation);

        // Act
        DecisionOutcome outcome = orchestrationService.evaluateDecision(claimId);

        // Assert Routing Target
        assertEquals(RoutingTarget.COVERAGE_REVIEW, outcome.getRoutingTarget(),
                "Decision should route to COVERAGE_REVIEW when date of loss is outside policy period");

        // Assert Status and Flags
        assertEquals(DecisionStatus.POLICY_PERIOD_VIOLATION, outcome.getStatus());
        assertFalse(outcome.isEligibleForAutoAdjudication());
        assertTrue(outcome.requiresManualReview());

        // Verify Side Effects: State Update in DynamoDB
        verify(claimRepository).save(argThat(savedClaim ->
                savedClaim.getId().equals(claimId) &&
                savedClaim.getRoutingTarget() == RoutingTarget.COVERAGE_REVIEW &&
                savedClaim.getDecisionStatus() == DecisionStatus.POLICY_PERIOD_VIOLATION
        ));

        // Verify Service Interactions
        verify(policyService).validatePolicyPeriod(claimId, dateOfLoss);
        
        // Coverage review may trigger internal tracking updates but typically no direct insured notification 
        // until reviewer action, verifying no SES call for this specific route.
        verifyNoInteractions(notificationService);
    }
}
