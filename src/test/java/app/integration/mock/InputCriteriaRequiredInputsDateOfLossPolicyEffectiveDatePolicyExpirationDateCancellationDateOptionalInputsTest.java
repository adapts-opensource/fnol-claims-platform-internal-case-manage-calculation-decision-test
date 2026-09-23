package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * JUnit 5 mock test for Claim Data Standardization: calculation:decision.
 * Verifies date validation, chronological ordering, freshness SLAs, policy period inclusion,
 * cancellation/moratorium routing, and edge-case handling. External I/O is mocked per NFRs.
 */
public class ClaimDataStandardizationDecisionTest {

    private PolicyPasService policyPasService;
    private RegulatoryFeedService regulatoryFeedService;
    private ClaimDecisionEngine claimDecisionEngine;

    @BeforeEach
    void setUp() {
        policyPasService = mock(PolicyPasService.class);
        regulatoryFeedService = mock(RegulatoryFeedService.class);
        claimDecisionEngine = new ClaimDecisionEngine(policyPasService, regulatoryFeedService);
    }

    @Test
    void testValidChronologicalDatesWithinPolicyPeriod() {
        LocalDate lossDate = LocalDate.of(2023, 10, 15);
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate expirationDate = LocalDate.of(2023, 12, 31);
        LocalDate cancellationDate = null;

        when(policyPasService.fetchPolicyDates(anyString()))
                .thenReturn(new PolicyData(effectiveDate, expirationDate, LocalDateTime.now().minusMinutes(2)));
        when(regulatoryFeedService.fetchStormMoratoriumDates(anyString()))
                .thenReturn(new RegulatoryData(null, null, null, null));

        DecisionResult result = claimDecisionEngine.calculateDecision(lossDate, effectiveDate, expirationDate, cancellationDate);

        assertEquals(DateValidationStatus.VALID, result.dateValidationStatus());
        assertEquals(RoutingRecommendation.STANDARD, result.routingRecommendation());
        assertFalse(result.coverageReviewFlag());
    }

    @Test
    void testOutOfPeriodRule() {
        LocalDate lossDate = LocalDate.of(2023, 10, 15);
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate expirationDate = LocalDate.of(2023, 10, 10);

        when(policyPasService.fetchPolicyDates(anyString()))
                .thenReturn(new PolicyData(effectiveDate, expirationDate, LocalDateTime.now().minusMinutes(1)));
        when(regulatoryFeedService.fetchStormMoratoriumDates(anyString()))
                .thenReturn(new RegulatoryData(null, null, null, null));

        DecisionResult result = claimDecisionEngine.calculateDecision(lossDate, effectiveDate, expirationDate, null);

        assertEquals(DateValidationStatus.OUT_OF_PERIOD, result.dateValidationStatus());
        assertEquals(RoutingRecommendation.COVERAGE_REVIEW, result.routingRecommendation());
        assertTrue(result.coverageReviewFlag());
    }

    @Test
    void testMoratoriumOverlapRule() {
        LocalDate lossDate = LocalDate.of(2023, 10, 15);
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate expirationDate = LocalDate.of(2023, 12, 31);
        LocalDate moratoriumStart = LocalDate.of(2023, 10, 14);
        LocalDate moratoriumEnd = LocalDate.of(2023, 10, 20);

        when(policyPasService.fetchPolicyDates(anyString()))
                .thenReturn(new PolicyData(effectiveDate, expirationDate, LocalDateTime.now().minusMinutes(2)));
        when(regulatoryFeedService.fetchStormMoratoriumDates(anyString()))
                .thenReturn(new RegulatoryData(null, moratoriumStart, moratoriumEnd, LocalDateTime.now().minusMinutes(30)));

        DecisionResult result = claimDecisionEngine.calculateDecision(lossDate, effectiveDate, expirationDate, null);

        assertEquals(DateValidationStatus.MORATORIUM_ACTIVE, result.dateValidationStatus());
        assertEquals(RoutingRecommendation.MORATORIUM_QUEUE, result.routingRecommendation());
        assertTrue(result.coverageReviewFlag());
    }

    @Test
    void testCancellationEffectiveAfterLossIgnored() {
        LocalDate lossDate = LocalDate.of(2023, 10, 15);
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate expirationDate = LocalDate.of(2023, 12, 31);
        LocalDate cancellationDate = LocalDate.of(2023, 10, 20);

        when(policyPasService.fetchPolicyDates(anyString()))
                .thenReturn(new PolicyData(effectiveDate, expirationDate, LocalDateTime.now().minusMinutes(2)));
        when(regulatoryFeedService.fetchStormMoratoriumDates(anyString()))
                .thenReturn(new RegulatoryData(null, null, null, null));

        DecisionResult result = claimDecisionEngine.calculateDecision(lossDate, effectiveDate, expirationDate, cancellationDate);

        assertEquals(DateValidationStatus.VALID, result.dateValidationStatus());
        assertEquals(RoutingRecommendation.STANDARD, result.routingRecommendation());
    }

    @Test
    void testInvalidDateFormatOrChronologicalOrderFailsValidation() {
        LocalDate lossDate = LocalDate.of(2023, 10, 15);
        LocalDate effectiveDate = LocalDate.of(2023, 11, 1); // loss before effective
        LocalDate expirationDate = LocalDate.of(2023, 12, 31);

        when(policyPasService.fetchPolicyDates(anyString()))
                .thenReturn(new PolicyData(effectiveDate, expirationDate, LocalDateTime.now().minusMinutes(2)));
        when(regulatoryFeedService.fetchStormMoratoriumDates(anyString()))
                .thenReturn(new RegulatoryData(null, null, null, null));

        assertThrows(InvalidInputException.class, () ->
                claimDecisionEngine.calculateDecision(lossDate, effectiveDate, expirationDate, null));
    }

    @Test
    void testPolicyFreshnessSLAViolationThrows() {
        LocalDate lossDate = LocalDate.of(2023, 10, 15);
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate expirationDate = LocalDate.of(2023, 12, 31);

        when(policyPasService.fetchPolicyDates(anyString()))
                .thenReturn(new PolicyData(effectiveDate, expirationDate, LocalDateTime.now().minusMinutes(6)));

        assertThrows(InvalidInputException.class, () ->
                claimDecisionEngine.calculateDecision(lossDate, effectiveDate, expirationDate, null));
    }

    @Test
    void testRegulatoryFeedFreshnessSLAViolationThrows() {
        LocalDate lossDate = LocalDate.of(2023, 10, 15);
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate expirationDate = LocalDate.of(2023, 12, 31);
        LocalDate moratoriumStart = LocalDate.of(2023, 10, 14);
        LocalDate moratoriumEnd = LocalDate.of(2023, 10, 20);

        when(policyPasService.fetchPolicyDates(anyString()))
                .thenReturn(new PolicyData(effectiveDate, expirationDate, LocalDateTime.now().minusMinutes(2)));
        when(regulatoryFeedService.fetchStormMoratoriumDates(anyString()))
                .thenReturn(new RegulatoryData(null, moratoriumStart, moratoriumEnd, LocalDateTime.now().minusHours(2)));

        assertThrows(InvalidInputException.class, () ->
                claimDecisionEngine.calculateDecision(lossDate, effectiveDate, expirationDate, null));
    }

    @Test
    void testPolicyRewrittenOnDateOfLossEdgeCase() {
        LocalDate lossDate = LocalDate.of(2023, 10, 15);
        LocalDate effectiveDate = LocalDate.of(2023, 10, 15);
        LocalDate expirationDate = LocalDate.of(2024, 10, 14);

        when(policyPasService.fetchPolicyDates(anyString()))
                .thenReturn(new PolicyData(effectiveDate, expirationDate, LocalDateTime.now().minusMinutes(1)));
        when(regulatoryFeedService.fetchStormMoratoriumDates(anyString()))
                .thenReturn(new RegulatoryData(null, null, null, null));

        DecisionResult result = claimDecisionEngine.calculateDecision(lossDate, effectiveDate, expirationDate, null);

        assertEquals(DateValidationStatus.VALID, result.dateValidationStatus());
        assertEquals(RoutingRecommendation.STANDARD, result.routingRecommendation());
        assertFalse(result.coverageReviewFlag());
    }

    // Domain interfaces representing external I/O contracts
    interface PolicyPasService {
        PolicyData fetchPolicyDates(String policyId);
    }

    interface RegulatoryFeedService {
        RegulatoryData fetchStormMoratoriumDates(String policyId);
    }

    record PolicyData(LocalDate effectiveDate, LocalDate expirationDate, LocalDateTime fetchedAt) {}
    record RegulatoryData(String stormEvent, LocalDate moratoriumStart, LocalDate moratoriumEnd, LocalDateTime fetchedAt) {}
    record DecisionResult(DateValidationStatus dateValidationStatus, RoutingRecommendation routingRecommendation, boolean coverageReviewFlag) {}

    enum DateValidationStatus { VALID, OUT_OF_PERIOD, MORATORIUM_ACTIVE, INVALID_FORMAT }
    enum RoutingRecommendation { STANDARD, OUT_OF_PERIOD, COVERAGE_REVIEW, MORATORIUM_QUEUE }

    static class InvalidInputException extends RuntimeException {
        InvalidInputException(String message) { super(message); }
    }

    /**
     * Minimal decision engine implementing the business rules.
     * Thread-safe via immutable records and stateless method.
     * Structured logging and TLS/IAM are enforced at the service boundary in production.
     */
    static class ClaimDecisionEngine {
        private final PolicyPasService policyPasService;
        private final RegulatoryFeedService regulatoryFeedService;

        ClaimDecisionEngine(PolicyPasService policyPasService, RegulatoryFeedService regulatoryFeedService) {
            this.policyPasService = policyPasService;
            this.regulatoryFeedService = regulatoryFeedService;
        }

        DecisionResult calculateDecision(LocalDate dateOfLoss, LocalDate policyEffectiveDate,
                                         LocalDate policyExpirationDate, LocalDate cancellationDate) {
            // 1. Chronological & Format Validation
            if (dateOfLoss.isBefore(policyEffectiveDate) || dateOfLoss.isAfter(policyExpirationDate)) {
                return new DecisionResult(DateValidationStatus.INVALID_FORMAT, RoutingRecommendation.STANDARD, false);
            }
            if (cancellationDate != null && cancellationDate.isBefore(dateOfLoss)) {
                return new DecisionResult(DateValidationStatus.OUT_OF_PERIOD, RoutingRecommendation.COVERAGE_REVIEW, true);
            }

            // 2. Freshness SLA Checks (PAS: 5 min, Regulatory: 1 hr)
            PolicyData policyData = policyPasService.fetchPolicyDates("policy-123");
            if (policyData.fetchedAt().isBefore(LocalDateTime.now().minusMinutes(5))) {
                throw new InvalidInputException("Policy data freshness SLA exceeded (>5 min).");
            }
            RegulatoryData regData = regulatoryFeedService.fetchStormMoratoriumDates("policy-123");
            if (regData.moratoriumStart() != null && regData.fetchedAt().isBefore(LocalDateTime.now().minusHours(1))) {
                throw new InvalidInputException("Regulatory feed freshness SLA exceeded (>1 hr).");
            }

            // 3. Moratorium/Storm Overlap Check
            if (regData.moratoriumStart() != null && regData.moratoriumEnd() != null) {
                if (!dateOfLoss.isBefore(regData.moratoriumStart()) && !dateOfLoss.isAfter(regData.moratoriumEnd())) {
                    return new DecisionResult(DateValidationStatus.MORATORIUM_ACTIVE, RoutingRecommendation.MORATORIUM_QUEUE, true);
                }
            }

            // 4. Policy Period Inclusion Decision
            if (dateOfLoss.isAfter(policyExpirationDate) || dateOfLoss.isBefore(policyEffectiveDate)) {
                return new DecisionResult(DateValidationStatus.OUT_OF_PERIOD, RoutingRecommendation.COVERAGE_REVIEW, true);
            }

            // 5. Default Standard Routing (cancellations effective after loss are ignored per rules)
            return new DecisionResult(DateValidationStatus.VALID, RoutingRecommendation.STANDARD, false);
        }
    }
}
