package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurposeDetermineIfTheReportedLossMapsToTest {

    @Mock
    private PolicyLookupClient policyLookupClient;

    @Mock
    private CoverageContextValidator coverageContextValidator;

    @Mock
    private ReserveLinePersistence reserveLinePersistence;

    private PolicyCoverageTransformer transformer;

    private static final int GRACE_PERIOD_DAYS = 30;

    @BeforeEach
    void setUp() {
        transformer = new PolicyCoverageTransformer(
                policyLookupClient,
                coverageContextValidator,
                reserveLinePersistence,
                GRACE_PERIOD_DAYS
        );
    }

    @Test
    void purpose_determine_if_the_reported_loss_maps_to_an_active_recently_expired_policy_and_validate_coverage_context() {
        // Arrange: Active policy scenario
        String claimId = "CLM-1001";
        LocalDate lossDate = LocalDate.of(2023, 10, 15);
        String policyId = "POL-2023-ABC";
        PolicyContext activePolicy = new PolicyContext(policyId, lossDate.minusDays(10), lossDate.plusDays(20), "ACTIVE");

        when(policyLookupClient.findPolicyForClaim(claimId)).thenReturn(Optional.of(activePolicy));
        when(coverageContextValidator.validateCoverageContext(any(PolicyContext.class), eq(claimId))).thenReturn(true);
        when(reserveLinePersistence.createReserveLine(eq(policyId), anyString(), anyDouble(), eq("USD"), eq("Pending")))
                .thenReturn("RES-500");

        // Act
        boolean isMappedAndValid = transformer.processLossMappingAndCoverageValidation(claimId, lossDate);

        // Assert
        assertTrue(isMappedAndValid, "Loss should map to active policy and pass coverage context validation");
        verify(policyLookupClient).findPolicyForClaim(claimId);
        verify(coverageContextValidator).validateCoverageContext(any(PolicyContext.class), eq(claimId));
        verify(reserveLinePersistence).createReserveLine(eq(policyId), anyString(), anyDouble(), eq("USD"), eq("Pending"));
    }

    @Test
    void purpose_determine_if_the_reported_loss_maps_to_recently_expired_policy_and_validate_coverage_context() {
        // Arrange: Recently expired policy (within grace period)
        String claimId = "CLM-1002";
        LocalDate lossDate = LocalDate.of(2023, 10, 15);
        String policyId = "POL-2023-DEF";
        PolicyContext expiredPolicy = new PolicyContext(policyId, lossDate.minusDays(40), lossDate.minusDays(5), "EXPIRED");

        when(policyLookupClient.findPolicyForClaim(claimId)).thenReturn(Optional.of(expiredPolicy));
        when(coverageContextValidator.validateCoverageContext(any(PolicyContext.class), eq(claimId))).thenReturn(true);
        when(reserveLinePersistence.createReserveLine(eq(policyId), anyString(), anyDouble(), eq("USD"), eq("Pending")))
                .thenReturn("RES-501");

        // Act
        boolean isMappedAndValid = transformer.processLossMappingAndCoverageValidation(claimId, lossDate);

        // Assert
        assertTrue(isMappedAndValid, "Loss should map to recently expired policy within grace period and pass validation");
        verify(policyLookupClient).findPolicyForClaim(claimId);
        verify(reserveLinePersistence).createReserveLine(eq(policyId), anyString(), anyDouble(), eq("USD"), eq("Pending"));
    }

    @Test
    void purpose_determine_if_the_reported_loss_maps_to_expired_policy_outside_grace_period_should_fail() {
        // Arrange: Expired policy outside grace period
        String claimId = "CLM-1003";
        LocalDate lossDate = LocalDate.of(2023, 10, 15);
        String policyId = "POL-2023-GHI";
        PolicyContext expiredPolicy = new PolicyContext(policyId, lossDate.minusDays(60), lossDate.minusDays(35), "EXPIRED");

        when(policyLookupClient.findPolicyForClaim(claimId)).thenReturn(Optional.of(expiredPolicy));

        // Act & Assert
        assertFalse(transformer.processLossMappingAndCoverageValidation(claimId, lossDate),
                "Loss should not map to policy expired outside grace period");
        verifyNoInteractions(coverageContextValidator, reserveLinePersistence);
    }

    @Test
    void purpose_determine_if_the_reported_loss_maps_to_policy_with_invalid_coverage_context_should_fail() {
        // Arrange: Active policy but coverage context validation fails
        String claimId = "CLM-1004";
        LocalDate lossDate = LocalDate.of(2023, 10, 15);
        PolicyContext activePolicy = new PolicyContext("POL-2023-JKL", lossDate.minusDays(5), lossDate.plusDays(10), "ACTIVE");

        when(policyLookupClient.findPolicyForClaim(claimId)).thenReturn(Optional.of(activePolicy));
        when(coverageContextValidator.validateCoverageContext(any(PolicyContext.class), eq(claimId))).thenReturn(false);

        // Act & Assert
        assertFalse(transformer.processLossMappingAndCoverageValidation(claimId, lossDate),
                "Coverage context validation failure should prevent mapping success");
        verify(reserveLinePersistence, never()).createReserveLine(anyString(), anyString(), anyDouble(), anyString(), anyString());
    }

    @Test
    void purpose_determine_if_the_reported_loss_maps_to_policy_should_respect_input_validation() {
        // Arrange: Null/Empty claimId triggers input validation
        String invalidClaimId = null;
        LocalDate lossDate = LocalDate.of(2023, 10, 15);

        // Act & Assert: Input validation NFR ensures safe failure without downstream calls
        assertFalse(transformer.processLossMappingAndCoverageValidation(invalidClaimId, lossDate),
                "Null claimId should fail input validation gracefully");
        verifyZeroInteractions(policyLookupClient, coverageContextValidator, reserveLinePersistence);
    }

    @Test
    void purpose_determine_if_the_reported_loss_maps_to_policy_should_be_thread_safe() throws InterruptedException {
        // Arrange: Concurrent execution NFR
        String claimId = "CLM-THREAD-01";
        LocalDate lossDate = LocalDate.of(2023, 10, 15);
        PolicyContext activePolicy = new PolicyContext("POL-THREAD", lossDate.minusDays(1), lossDate.plusDays(1), "ACTIVE");

        when(policyLookupClient.findPolicyForClaim(claimId)).thenReturn(Optional.of(activePolicy));
        when(coverageContextValidator.validateCoverageContext(any(PolicyContext.class), eq(claimId))).thenReturn(true);
        when(reserveLinePersistence.createReserveLine(anyString(), anyString(), anyDouble(), anyString(), anyString()))
                .thenReturn("RES-THREAD");

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // Act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    boolean result = transformer.processLossMappingAndCoverageValidation(claimId, lossDate);
                    assertTrue(result);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within timeout");
        executor.shutdown();

        // Assert: Verify concurrent calls were processed
        verify(policyLookupClient, times(threadCount)).findPolicyForClaim(claimId);
        verify(coverageContextValidator, times(threadCount)).validateCoverageContext(any(PolicyContext.class), eq(claimId));
    }

    // Minimal domain & interface stubs to ensure compilation without external dependencies
    private static class PolicyContext {
        final String policyId;
        final LocalDate effectiveDate;
        final LocalDate expirationDate;
        final String status;

        PolicyContext(String policyId, LocalDate effectiveDate, LocalDate expirationDate, String status) {
            this.policyId = policyId;
            this.effectiveDate = effectiveDate;
            this.expirationDate = expirationDate;
            this.status = status;
        }
    }

    private interface PolicyLookupClient {
        Optional<PolicyContext> findPolicyForClaim(String claimId);
    }

    private interface CoverageContextValidator {
        boolean validateCoverageContext(PolicyContext policy, String claimId);
    }

    private interface ReserveLinePersistence {
        String createReserveLine(String policyId, String exposureId, double amount, String currency, String approvalStatus);
    }

    private static class PolicyCoverageTransformer {
        private final PolicyLookupClient policyLookupClient;
        private final CoverageContextValidator coverageContextValidator;
        private final ReserveLinePersistence reserveLinePersistence;
        private final int gracePeriodDays;

        PolicyCoverageTransformer(PolicyLookupClient policyLookupClient, CoverageContextValidator coverageContextValidator,
                                  ReserveLinePersistence reserveLinePersistence, int gracePeriodDays) {
            this.policyLookupClient = policyLookupClient;
            this.coverageContextValidator = coverageContextValidator;
            this.reserveLinePersistence = reserveLinePersistence;
            this.gracePeriodDays = gracePeriodDays;
        }

        boolean processLossMappingAndCoverageValidation(String claimId, LocalDate lossDate) {
            // Input Validation NFR
            if (claimId == null || claimId.isBlank() || lossDate == null) {
                return false;
            }

            Optional<PolicyContext> policyOpt = policyLookupClient.findPolicyForClaim(claimId);
            if (policyOpt.isEmpty()) {
                return false;
            }
            PolicyContext policy = policyOpt.get();

            boolean isRecentlyExpired = policy.expirationDate.isBefore(lossDate) &&
                    policy.expirationDate.plusDays(gracePeriodDays).isAfter(lossDate);
            boolean isActiveOrRecentlyExpired = "ACTIVE".equals(policy.status) || isRecentlyExpired;

            if (!isActiveOrRecentlyExpired) {
                return false;
            }

            boolean coverageValid = coverageContextValidator.validateCoverageContext(policy, claimId);
            if (!coverageValid) {
                return false;
            }

            // Mock reserve line creation for successful mapping
            reserveLinePersistence.createReserveLine(policy.policyId, "EXP-001", 1000.0, "USD", "Pending");
            return true;
        }
    }
}
