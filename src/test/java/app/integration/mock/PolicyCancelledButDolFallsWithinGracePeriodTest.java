package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ClaimInitiationRoutingDecisionCalculationMockTest {

    private PolicyService policyService;
    private GracePeriodService gracePeriodService;
    private ClaimDecisionService claimDecisionService;

    @BeforeEach
    void setUp() {
        policyService = mock(PolicyService.class);
        gracePeriodService = mock(GracePeriodService.class);
        claimDecisionService = new ClaimDecisionService(policyService, gracePeriodService);
    }

    @Test
    @DisplayName("Policy cancelled but DoL falls within grace period")
    void policy_cancelled_but_dol_falls_within_grace_period() {
        // Given
        String policyId = "POL-GRACE-001";
        String claimId = "CLM-INIT-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("policyId", policyId);
        payload.put("dateOfLoss", "2023-10-01");
        payload.put("policyEffectiveDate", "2023-09-01");
        payload.put("gracePeriodDays", 30);

        when(policyService.getStatus(policyId)).thenReturn("CANCELLED");
        when(gracePeriodService.isWithinGracePeriod("2023-10-01", "2023-09-01", 30)).thenReturn(true);

        // When
        RoutingDecision decision = claimDecisionService.calculateDecision(payload);

        // Then
        assertNotNull(decision, "Routing decision must not be null");
        assertEquals("COVERAGE_VALID", decision.getStatus(), "Coverage should be valid due to grace period override");
        assertEquals("STANDARD_INTAKE_ROUTER", decision.getTarget(), "Should route to standard intake handler");
        assertEquals(claimId, decision.getClaimId(), "Claim ID must match initiation payload");

        verify(policyService).getStatus(policyId);
        verify(gracePeriodService).isWithinGracePeriod("2023-10-01", "2023-09-01", 30);
        verifyNoMoreInteractions(policyService, gracePeriodService);
    }

    // --- Dependency Mocks & SUT (Package-private for test isolation) ---

    static class PolicyService {
        public String getStatus(String policyId) { return null; }
    }

    static class GracePeriodService {
        public boolean isWithinGracePeriod(String dateOfLoss, String effectiveDate, int graceDays) { return false; }
    }

    static class RoutingDecision {
        private final String claimId;
        private final String status;
        private final String target;

        RoutingDecision(String claimId, String status, String target) {
            this.claimId = claimId;
            this.status = status;
            this.target = target;
        }

        public String getClaimId() { return claimId; }
        public String getStatus() { return status; }
        public String getTarget() { return target; }
    }

    static class ClaimDecisionService {
        private final PolicyService policyService;
        private final GracePeriodService gracePeriodService;

        ClaimDecisionService(PolicyService policyService, GracePeriodService gracePeriodService) {
            this.policyService = policyService;
            this.gracePeriodService = gracePeriodService;
        }

        RoutingDecision calculateDecision(Map<String, Object> payload) {
            String claimId = (String) payload.get("id");
            String policyId = (String) payload.get("policyId");
            String dateOfLoss = (String) payload.get("dateOfLoss");
            String effectiveDate = (String) payload.get("policyEffectiveDate");
            int graceDays = (int) payload.get("gracePeriodDays");

            // Input validation (NFR: input_validation)
            if (claimId == null || policyId == null || dateOfLoss == null || effectiveDate == null) {
                throw new IllegalArgumentException("Payload must contain required claim and policy fields");
            }

            String policyStatus = policyService.getStatus(policyId);
            boolean isInGracePeriod = gracePeriodService.isWithinGracePeriod(dateOfLoss, effectiveDate, graceDays);

            // Decision logic: Grace period overrides cancellation status
            String coverageStatus = (policyStatus.equals("CANCELLED") && isInGracePeriod)
                    ? "COVERAGE_VALID" : policyStatus;

            // Structured logging placeholder (NFR: observability)
            // logger.atInfo().setMessage("Calculated decision for claim {}").addKeyValue("claimId", claimId).log();

            return new RoutingDecision(claimId, coverageStatus, "STANDARD_INTAKE_ROUTER");
        }
    }
}
