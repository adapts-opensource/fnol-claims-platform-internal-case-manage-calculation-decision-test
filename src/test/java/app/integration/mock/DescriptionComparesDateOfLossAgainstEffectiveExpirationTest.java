package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CoverageWindowDecisionOrchestratorTest {

    @Mock
    private PolicyStateService policyStateService;

    @Mock
    private StormEventService stormEventService;

    @Mock
    private ComplianceDateService complianceDateService;

    @InjectMocks
    private CoverageDecisionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(policyStateService.getPolicyState(anyString())).thenReturn(
                new PolicyState(
                        LocalDate.of(2023, 1, 1),
                        LocalDate.of(2023, 12, 31),
                        null, null, null, null
                )
        );
        when(stormEventService.getKnownStormEvents(anyString())).thenReturn(Collections.emptyList());
        when(complianceDateService.getMoratoriumDate(anyString())).thenReturn(null);
    }

    @Test
    void description_compares_date_of_loss_against_effective_expiration_cancellation_reinstatement_rewrite_binding_restrictions_moratorium_dates_and_known_storm_events_determines_coverage_window_status_and_flags_for_review_if_outside_period() {
        // Arrange: Deliberately set date of loss before the effective date to trigger out-of-period logic
        LocalDate dateOfLoss = LocalDate.of(2022, 12, 20);

        // Act
        CoverageDecisionResult result = orchestrator.evaluateCoverageWindow("POL-123", dateOfLoss);

        // Assert
        assertEquals(CoverageWindowStatus.OUTSIDE_PERIOD, result.status());
        assertTrue(result.flaggedForReview(), "Should flag for review when loss date is outside coverage period");
        assertNotNull(result.reviewReason(), "Review reason must be populated for out-of-period evaluations");

        verify(policyStateService).getPolicyState("POL-123");
        verify(stormEventService).getKnownStormEvents("POL-123");
        verify(complianceDateService).getMoratoriumDate("POL-123");
    }
}

enum CoverageWindowStatus { ACTIVE, OUTSIDE_PERIOD, UNDER_REVIEW }

record CoverageDecisionResult(CoverageWindowStatus status, boolean flaggedForReview, String reviewReason) {}

record PolicyState(LocalDate effectiveDate, LocalDate expirationDate, LocalDate cancellationDate,
                   LocalDate reinstatementDate, LocalDate rewriteDate, LocalDate bindingRestrictionDate) {}

interface PolicyStateService { PolicyState getPolicyState(String policyId); }
interface StormEventService { List<String> getKnownStormEvents(String policyId); }
interface ComplianceDateService { LocalDate getMoratoriumDate(String policyId); }

class CoverageDecisionOrchestrator {
    private final PolicyStateService policyStateService;
    private final StormEventService stormEventService;
    private final ComplianceDateService complianceDateService;

    CoverageDecisionOrchestrator(PolicyStateService policyStateService, StormEventService stormEventService, ComplianceDateService complianceDateService) {
        this.policyStateService = policyStateService;
        this.stormEventService = stormEventService;
        this.complianceDateService = complianceDateService;
    }

    CoverageDecisionResult evaluateCoverageWindow(String policyId, LocalDate dateOfLoss) {
        PolicyState policy = policyStateService.getPolicyState(policyId);
        LocalDate effective = policy.effectiveDate();
        LocalDate expiration = policy.expirationDate();
        LocalDate cancellation = policy.cancellationDate();
        LocalDate reinstatement = policy.reinstatementDate();
        LocalDate rewrite = policy.rewriteDate();
        LocalDate bindingRestriction = policy.bindingRestrictionDate();
        LocalDate moratorium = complianceDateService.getMoratoriumDate(policyId);
        List<String> storms = stormEventService.getKnownStormEvents(policyId);

        boolean isOutsidePeriod = false;

        if (dateOfLoss.isBefore(effective) || dateOfLoss.isAfter(expiration)) {
            isOutsidePeriod = true;
        }
        if (cancellation != null && dateOfLoss.isAfter(cancellation)) {
            isOutsidePeriod = true;
        }
        if (reinstatement != null && dateOfLoss.isBefore(reinstatement)) {
            isOutsidePeriod = true;
        }
        if (rewrite != null && dateOfLoss.isBefore(rewrite)) {
            isOutsidePeriod = true;
        }
        if (bindingRestriction != null && dateOfLoss.isBefore(bindingRestriction)) {
            isOutsidePeriod = true;
        }
        if (moratorium != null && (dateOfLoss.isEqual(moratorium) || dateOfLoss.isAfter(moratorium))) {
            isOutsidePeriod = true;
        }
        if (!storms.isEmpty()) {
            isOutsidePeriod = true;
        }

        return new CoverageDecisionResult(
                isOutsidePeriod ? CoverageWindowStatus.OUTSIDE_PERIOD : CoverageWindowStatus.ACTIVE,
                isOutsidePeriod,
                isOutsidePeriod ? "Loss date falls outside valid coverage window or active restrictions" : null
        );
    }
}
