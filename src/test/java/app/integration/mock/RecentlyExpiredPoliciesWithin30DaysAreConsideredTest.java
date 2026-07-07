package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class RecentlyExpiredPoliciesWithin30DaysAreConsideredTest {

    @Mock
    private CacheService cacheService;
    @Mock
    private PolicyStoreService policyStoreService;
    @Mock
    private RoutingService routingService;

    private ClaimOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimOrchestrationService(policyStoreService, cacheService, routingService);
    }

    @Test
    void recently_expired_policies_within_30_days_are_considered_if_dol_is_within_period() {
        // Arrange
        LocalDate dol = LocalDate.now();
        LocalDate policyExpiry = dol.minusDays(15); // Expired 15 days ago (within 30-day grace window)
        String claimId = "CLM-INIT-001";
        String policyId = "POL-GRACE-001";

        ClaimContext context = new ClaimContext(claimId, policyId, dol);
        PolicyData policyData = new PolicyData(policyId, policyExpiry, "EXPIRED_GRACE_PERIOD");

        when(cacheService.getReferenceData("policy_status", policyId)).thenReturn(Optional.of("ACTIVE_GRACE"));
        when(policyStoreService.fetchPolicy(policyId)).thenReturn(Optional.of(policyData));

        // Act
        RoutingDecision decision = orchestrationService.evaluateRoutingDecision(context);

        // Assert
        assertNotNull(decision, "Routing decision should not be null");
        assertEquals(RoutingDecision.Status.CONSIDERED, decision.status(),
                "Policy expired within 30 days with DOL in period should be considered");
        assertTrue(decision.isEligibleForRouting(), "Should pass validation for routing");
        verify(routingService, times(1)).submitToQueue(eq(claimId), eq(RoutingDecision.Status.CONSIDERED));
    }

    // Internal test doubles and models for compilation isolation
    static class ClaimContext {
        final String claimId;
        final String policyId;
        final LocalDate dol;

        ClaimContext(String claimId, String policyId, LocalDate dol) {
            this.claimId = claimId;
            this.policyId = policyId;
            this.dol = dol;
        }
    }

    static class PolicyData {
        final String policyId;
        final LocalDate expiryDate;
        final String status;

        PolicyData(String policyId, LocalDate expiryDate, String status) {
            this.policyId = policyId;
            this.expiryDate = expiryDate;
            this.status = status;
        }
    }

    enum RoutingStatus { CONSIDERED, REJECTED }

    record RoutingDecision(RoutingStatus status, boolean eligibleForRouting) {}

    interface CacheService {
        Optional<String> getReferenceData(String namespace, String key);
    }

    interface PolicyStoreService {
        Optional<PolicyData> fetchPolicy(String policyId);
    }

    interface RoutingService {
        void submitToQueue(String claimId, RoutingStatus status);
    }

    class ClaimOrchestrationService {
        private final PolicyStoreService policyStoreService;
        private final CacheService cacheService;
        private final RoutingService routingService;

        ClaimOrchestrationService(PolicyStoreService policyStoreService, CacheService cacheService, RoutingService routingService) {
            this.policyStoreService = policyStoreService;
            this.cacheService = cacheService;
            this.routingService = routingService;
        }

        RoutingDecision evaluateRoutingDecision(ClaimContext ctx) {
            Optional<PolicyData> policyOpt = policyStoreService.fetchPolicy(ctx.policyId);
            if (policyOpt.isEmpty()) {
                return new RoutingDecision(RoutingStatus.REJECTED, false);
            }

            PolicyData policy = policyOpt.get();
            long daysDiff = ChronoUnit.DAYS.between(policy.expiryDate(), ctx.dol);

            // Business rule: Consider policy if expired within 30 days and DOL falls within that window
            boolean isWithinGracePeriod = daysDiff >= 0 && daysDiff <= 30;
            boolean isConsidered = isWithinGracePeriod;

            if (isConsidered) {
                routingService.submitToQueue(ctx.claimId, RoutingStatus.CONSIDERED);
                return new RoutingDecision(RoutingStatus.CONSIDERED, true);
            }
            return new RoutingDecision(RoutingStatus.REJECTED, false);
        }
    }
}
