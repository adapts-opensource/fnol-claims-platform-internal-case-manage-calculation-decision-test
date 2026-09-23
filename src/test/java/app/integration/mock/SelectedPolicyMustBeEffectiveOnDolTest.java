package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionMockTest {

    @Mock
    private PolicyService policyService;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private OrchestrationDecisionService decisionService;

    private static final String POLICY_ID = "POL-12345";
    private static final String CLAIM_ID = "CLM-67890";

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and injection
    }

    @Test
    void selected_policy_must_be_effective_on_dol() {
        // Arrange: Policy is active on the Date of Loss
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate expirationDate = LocalDate.of(2024, 12, 31);
        LocalDate dol = LocalDate.of(2023, 6, 15);

        when(policyService.getPolicyDetails(POLICY_ID))
                .thenReturn(Optional.of(new PolicyDetails(effectiveDate, expirationDate)));

        // Act
        Map<String, Object> decisionResult = decisionService.evaluateClaimRouting(CLAIM_ID, POLICY_ID, dol);

        // Assert
        assertNotNull(decisionResult);
        assertEquals("ROUTE_TO_UNDERWRITING", decisionResult.get("routingOutcome"));
        assertTrue((Boolean) decisionResult.get("policyEffectiveOnDol"));
        verify(policyService, times(1)).getPolicyDetails(POLICY_ID);
        verify(cacheService, times(1)).put(eq("validation:" + POLICY_ID), any());
    }

    @Test
    void selected_policy_must_be_effective_on_dol_when_expired_before_loss() {
        // Arrange
        LocalDate effectiveDate = LocalDate.of(2022, 1, 1);
        LocalDate expirationDate = LocalDate.of(2023, 5, 31);
        LocalDate dol = LocalDate.of(2023, 6, 15);

        when(policyService.getPolicyDetails(POLICY_ID))
                .thenReturn(Optional.of(new PolicyDetails(effectiveDate, expirationDate)));

        // Act
        Map<String, Object> decisionResult = decisionService.evaluateClaimRouting(CLAIM_ID, POLICY_ID, dol);

        // Assert
        assertNotNull(decisionResult);
        assertEquals("HOLD_FOR_REVIEW", decisionResult.get("routingOutcome"));
        assertFalse((Boolean) decisionResult.get("policyEffectiveOnDol"));
    }

    @Test
    void selected_policy_must_be_effective_on_dol_when_not_yet_effective() {
        // Arrange
        LocalDate effectiveDate = LocalDate.of(2023, 7, 1);
        LocalDate expirationDate = LocalDate.of(2024, 12, 31);
        LocalDate dol = LocalDate.of(2023, 6, 15);

        when(policyService.getPolicyDetails(POLICY_ID))
                .thenReturn(Optional.of(new PolicyDetails(effectiveDate, expirationDate)));

        // Act
        Map<String, Object> decisionResult = decisionService.evaluateClaimRouting(CLAIM_ID, POLICY_ID, dol);

        // Assert
        assertNotNull(decisionResult);
        assertEquals("HOLD_FOR_REVIEW", decisionResult.get("routingOutcome"));
        assertFalse((Boolean) decisionResult.get("policyEffectiveOnDol"));
    }

    // Infrastructure/Service Interfaces (Mocked)
    interface PolicyService {
        Optional<PolicyDetails> getPolicyDetails(String policyId);
    }

    interface CacheService {
        void put(String key, Object value);
        Optional<String> get(String key);
    }

    // Data Transfer Object
    static class PolicyDetails {
        private final LocalDate effectiveDate;
        private final LocalDate expirationDate;

        public PolicyDetails(LocalDate effectiveDate, LocalDate expirationDate) {
            this.effectiveDate = effectiveDate;
            this.expirationDate = expirationDate;
        }

        public LocalDate getEffectiveDate() { return effectiveDate; }
        public LocalDate getExpirationDate() { return expirationDate; }
    }

    // Service Under Test (Orchestration Logic)
    static class OrchestrationDecisionService {
        private PolicyService policyService;
        private CacheService cacheService;

        public void setPolicyService(PolicyService policyService) {
            this.policyService = policyService;
        }

        public void setCacheService(CacheService cacheService) {
            this.cacheService = cacheService;
        }

        public Map<String, Object> evaluateClaimRouting(String claimId, String policyId, LocalDate dol) {
            Optional<PolicyDetails> policyOpt = policyService.getPolicyDetails(policyId);
            if (policyOpt.isPresent()) {
                PolicyDetails policy = policyOpt.get();
                boolean isEffective = !dol.isBefore(policy.getEffectiveDate()) && !dol.isAfter(policy.getExpirationDate());

                // Cache policy validation result
                cacheService.put("validation:" + policyId, Map.of("effective", isEffective));

                return Map.of(
                        "policyEffectiveOnDol", isEffective,
                        "routingOutcome", isEffective ? "ROUTE_TO_UNDERWRITING" : "HOLD_FOR_REVIEW",
                        "claimId", claimId,
                        "dol", dol.toString()
                );
            }
            return Map.of(
                    "policyEffectiveOnDol", false,
                    "routingOutcome", "INVALID_POLICY",
                    "claimId", claimId
            );
        }
    }
}
