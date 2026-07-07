package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mocks external I/O and validates FNOL decision routing logic.
 * Ensures thread-safe, deterministic execution without live AWS/HTTP calls.
 */
public class MultiChannelFnolValidationMockTest {

    @Mock
    private PolicyService policyService;

    @Mock
    private RoutingService routingService;

    @Mock
    private ClaimsRepository claimsRepository;

    @InjectMocks
    private FnolDecisionService fnolDecisionService;

    @Test
    void if_dol_outside_policy_period_status_coverage_review_required_route_to_manual_review() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        String policyId = "POL-98765";
        String tenantId = "NEWCO-TENANT-01";
        LocalDate dateOfLoss = LocalDate.now().minusDays(30);
        LocalDate policyStartDate = LocalDate.now().minusDays(10);
        LocalDate policyEndDate = LocalDate.now().minusDays(1);

        when(policyService.getActivePolicyPeriod(policyId)).thenReturn(new PolicyPeriod(policyStartDate, policyEndDate));

        FnolSubmission fnol = new FnolSubmission(claimId, policyId, tenantId, dateOfLoss);

        // Act
        DecisionOutcome outcome = fnolDecisionService.evaluate(fnol);

        // Assert
        assertEquals(CoverageStatus.COVERAGE_REVIEW_REQUIRED, outcome.getStatus());
        assertEquals(RoutingDestination.MANUAL_REVIEW, outcome.getRoutingDestination());
        verify(claimsRepository, times(1)).save(eq(claimId), any());
        verify(routingService, times(1)).route(eq(claimId), eq(tenantId), eq(RoutingDestination.MANUAL_REVIEW));
    }
}

// Domain & Service stubs for isolated mock testing
record PolicyPeriod(LocalDate start, LocalDate end) {}
record FnolSubmission(String claimId, String policyId, String tenantId, LocalDate dateOfLoss) {}
record DecisionOutcome(CoverageStatus status, RoutingDestination routingDestination) {}
enum CoverageStatus { COVERAGE_REVIEW_REQUIRED, ACCEPTED }
enum RoutingDestination { MANUAL_REVIEW, AUTO_ADJUSTER }
interface PolicyService { PolicyPeriod getActivePolicyPeriod(String policyId); }
interface RoutingService { void route(String claimId, String tenantId, RoutingDestination dest); }
interface ClaimsRepository { void save(String claimId, Object claim); }
class FnolDecisionService {
    private final PolicyService policyService;
    private final RoutingService routingService;
    private final ClaimsRepository claimsRepository;
    FnolDecisionService(PolicyService ps, RoutingService rs, ClaimsRepository cr) {
        this.policyService = ps; this.routingService = rs; this.claimsRepository = cr;
    }
    DecisionOutcome evaluate(FnolSubmission fnol) {
        PolicyPeriod period = policyService.getActivePolicyPeriod(fnol.policyId());
        boolean outside = fnol.dateOfLoss().isBefore(period.start()) || fnol.dateOfLoss().isAfter(period.end());
        if (outside) {
            claimsRepository.save(fnol.claimId(), null);
            routingService.route(fnol.claimId(), fnol.tenantId(), RoutingDestination.MANUAL_REVIEW);
            return new DecisionOutcome(CoverageStatus.COVERAGE_REVIEW_REQUIRED, RoutingDestination.MANUAL_REVIEW);
        }
        return new DecisionOutcome(CoverageStatus.ACCEPTED, RoutingDestination.AUTO_ADJUSTER);
    }
}
