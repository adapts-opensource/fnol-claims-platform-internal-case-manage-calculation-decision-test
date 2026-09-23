package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementStateTransitionMockTest {

    @Mock
    private PolicyPeriodValidator policyPeriodValidator;

    @Mock
    private StateTransitionRepository stateTransitionRepository;

    @InjectMocks
    private InsuredEngagementStateTransitionService stateTransitionService;

    private LocalDate dateOfLoss;
    private LocalDate policyStartDate;
    private LocalDate policyEndDate;
    private String claimId;

    @BeforeEach
    void setUp() {
        claimId = "CLM-2024-98765";
        dateOfLoss = LocalDate.of(2024, 1, 15);
        policyStartDate = LocalDate.of(2024, 2, 1);
        policyEndDate = LocalDate.of(2024, 2, 28);
    }

    @Test
    void if_dol_is_outside_policy_period_mark_as_out_of_period_but_retain_for_coverage_review() {
        // Arrange: DOL falls outside the active policy window
        when(policyPeriodValidator.isWithinPolicyPeriod(eq(claimId), eq(policyStartDate), eq(policyEndDate), eq(dateOfLoss)))
                .thenReturn(false);

        // Act: Trigger state transition logic
        stateTransitionService.processEngagementStateTransition(claimId, dateOfLoss, policyStartDate, policyEndDate);

        // Assert: Verify state update was persisted with correct flags
        ArgumentCaptor<ClaimStateUpdate> captor = ArgumentCaptor.forClass(ClaimStateUpdate.class);
        verify(stateTransitionRepository).save(captor.capture());

        ClaimStateUpdate updatedState = captor.getValue();
        assertEquals(TransitionState.OUT_OF_PERIOD, updatedState.getState());
        assertTrue(updatedState.isRetainForCoverageReview(), "Claim should be retained for coverage review despite out-of-period DOL");
        assertEquals(claimId, updatedState.getClaimId());
        assertEquals(dateOfLoss, updatedState.getDateOfLoss());

        // Verify no termination or rejection was triggered
        verifyNoInteractions(stateTransitionRepository); // reset mock to check specific calls if needed, or use verifyZeroInteractions for other methods
        verify(stateTransitionRepository, times(1)).save(any());
    }

    // Minimal domain types for compilation context
    enum TransitionState { IN_COVERAGE, OUT_OF_PERIOD, TERMINATED, REJECTED }

    record ClaimStateUpdate(String claimId, LocalDate dateOfLoss, TransitionState state, boolean retainForCoverageReview) {}

    interface PolicyPeriodValidator {
        boolean isWithinPolicyPeriod(String claimId, LocalDate startDate, LocalDate endDate, LocalDate dol);
    }

    interface StateTransitionRepository {
        void save(ClaimStateUpdate update);
    }

    static class InsuredEngagementStateTransitionService {
        private final PolicyPeriodValidator policyPeriodValidator;
        private final StateTransitionRepository stateTransitionRepository;

        InsuredEngagementStateTransitionService(PolicyPeriodValidator policyPeriodValidator, StateTransitionRepository stateTransitionRepository) {
            this.policyPeriodValidator = policyPeriodValidator;
            this.stateTransitionRepository = stateTransitionRepository;
        }

        void processEngagementStateTransition(String claimId, LocalDate dateOfLoss, LocalDate policyStartDate, LocalDate policyEndDate) {
            boolean withinPolicy = policyPeriodValidator.isWithinPolicyPeriod(claimId, policyStartDate, policyEndDate, dateOfLoss);
            TransitionState newState = withinPolicy ? TransitionState.IN_COVERAGE : TransitionState.OUT_OF_PERIOD;
            boolean retainReview = !withinPolicy; // Retain for review when out of period
            stateTransitionRepository.save(new ClaimStateUpdate(claimId, dateOfLoss, newState, retainReview));
        }
    }
}
