package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurposeMatchFnolToPolicyAndValidateDol {

    @Mock
    private PolicyLookupService policyLookupService;

    @Mock
    private PolicyRestrictionEngine restrictionEngine;

    @InjectMocks
    private InsuredEngagementStateTransitionEngine stateTransitionEngine;

    private static final String FNOL_ID = "fnol-98765";
    private static final String POLICY_NUMBER = "POL-112233";
    private static final Instant DOL_WITHIN_PERIOD = Instant.parse("2023-06-15T10:00:00Z");
    private static final Instant EFFECTIVE_DATE = Instant.parse("2023-01-01T00:00:00Z");
    private static final Instant EXPIRATION_DATE = Instant.parse("2023-12-31T23:59:59Z");

    @BeforeEach
    void setUp() {
        reset(policyLookupService, restrictionEngine);
    }

    @Test
    void purpose_match_fnol_to_policy_and_validate_dol_against_policy_period_and_restrictions() {
        PolicyRecord validPolicy = new PolicyRecord(POLICY_NUMBER, EFFECTIVE_DATE, EXPIRATION_DATE);
        when(policyLookupService.resolvePolicyForFnol(FNOL_ID, POLICY_NUMBER)).thenReturn(Optional.of(validPolicy));
        when(restrictionEngine.checkCoverageEligibility(validPolicy, DOL_WITHIN_PERIOD)).thenReturn(true);

        TransitionOutcome outcome = stateTransitionEngine.evaluateAndTransition(FNOL_ID, POLICY_NUMBER, DOL_WITHIN_PERIOD);

        assertEquals(TransitionOutcome.Status.APPROVED, outcome.status());
        assertTrue(outcome.message().contains("DOL validated successfully"));
        verify(policyLookupService).resolvePolicyForFnol(FNOL_ID, POLICY_NUMBER);
        verify(restrictionEngine).checkCoverageEligibility(validPolicy, DOL_WITHIN_PERIOD);
    }
}
