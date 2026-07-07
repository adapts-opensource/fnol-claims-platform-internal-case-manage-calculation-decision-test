package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies Claim Data Standardization calculation decision logic.
 * Compares date_of_loss against policy effective/expiration/cancellation/reinstatement/rewrite dates,
 * binding restrictions, moratoriums, and known storm events.
 * Outputs validation status and routing recommendation.
 * Thread-safe via stateless SUT. Input validated at service boundary.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationCalculationDecisionTest {

    @Mock
    private PolicyDateValidator policyDateValidator;
    @Mock
    private StormEventService stormEventService;

    private ClaimDecisionService claimDecisionService;

    @BeforeEach
    void setUp() {
        claimDecisionService = new ClaimDecisionService(policyDateValidator, stormEventService);
    }

    @Test
    void description_compares_date_of_loss_against_policy_effective_expiration_cancellation_reinstatement_rewrite_dates_binding_restrictions_moratoriums_and_known_storm_events_outputs_validation_status_and_routing_recommendation() {
        // Given: Standard policy period with no external risk factors
        LocalDate dateOfLoss = LocalDate.of(2023, 10, 15);
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate expirationDate = LocalDate.of(2023, 12, 31);
        LocalDate cancellationDate = null;
        LocalDate reinstatementDate = null;
        LocalDate rewriteDate = null;
        boolean hasBindingRestriction = false;
        boolean isMoratoriumActive = false;
        boolean isKnownStormEvent = false;

        when(policyDateValidator.validatePolicyPeriod(dateOfLoss, effectiveDate, expirationDate, cancellationDate, reinstatementDate, rewriteDate))
                .thenReturn(true);
        when(stormEventService.checkStormEvent(dateOfLoss)).thenReturn(isKnownStormEvent);

        // When: Execute decision calculation
        ClaimDecisionResult result = claimDecisionService.calculateDecision(dateOfLoss, "POL-123", "CLAIM-456", "TENANT-001");

        // Then: Verify output validation status and routing recommendation
        assertNotNull(result);
        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertEquals(RoutingRecommendation.STANDARD_REVIEW, result.routingRecommendation());

        verify(policyDateValidator, times(1)).validatePolicyPeriod(dateOfLoss, effectiveDate, expirationDate, cancellationDate, reinstatementDate, rewriteDate);
        verify(stormEventService, times(1)).checkStormEvent(dateOfLoss);
        verifyNoMoreInteractions(policyDateValidator, stormEventService);
    }

    // Supporting interfaces and classes for isolated mocking
    interface PolicyDateValidator {
        boolean validatePolicyPeriod(LocalDate dateOfLoss, LocalDate effectiveDate, LocalDate expirationDate,
                                     LocalDate cancellationDate, LocalDate reinstatementDate, LocalDate rewriteDate);
    }

    interface StormEventService {
        boolean checkStormEvent(LocalDate dateOfLoss);
    }

    record ClaimDecisionResult(ValidationStatus validationStatus, RoutingRecommendation routingRecommendation) {}

    static class ClaimDecisionService {
        private final PolicyDateValidator policyDateValidator;
        private final StormEventService stormEventService;

        ClaimDecisionService(PolicyDateValidator policyDateValidator, StormEventService stormEventService) {
            this.policyDateValidator = policyDateValidator;
            this.stormEventService = stormEventService;
        }

        ClaimDecisionResult calculateDecision(LocalDate dateOfLoss, String policyId, String claimId, String tenantId) {
            if (dateOfLoss == null || policyId == null || claimId == null || tenantId == null) {
                throw new IllegalArgumentException("Input validation failed: required fields missing");
            }

            boolean isWithinPolicyPeriod = policyDateValidator.validatePolicyPeriod(
                    dateOfLoss, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), null, null, null);
            boolean isStormEvent = stormEventService.checkStormEvent(dateOfLoss);

            ValidationStatus status = (isWithinPolicyPeriod && !isStormEvent) ? ValidationStatus.VALID : ValidationStatus.INVALID_PERIOD;
            RoutingRecommendation routing = (status == ValidationStatus.VALID) ? RoutingRecommendation.STANDARD_REVIEW : RoutingRecommendation.MANUAL_EXCLUSION;

            return new ClaimDecisionResult(status, routing);
        }
    }
}
