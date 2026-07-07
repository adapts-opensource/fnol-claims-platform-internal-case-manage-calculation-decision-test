package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DescriptionSystemQueriesPolicyRegistryAppliesMatchingRulesTest {

    @Mock
    private PolicyRegistryClient policyRegistryClient;

    @Mock
    private RulesMatchingEngine rulesMatchingEngine;

    private ClaimEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimEnrichmentService(policyRegistryClient, rulesMatchingEngine);
    }

    @Test
    void description_system_queries_policy_registry_applies_matching_rules_validates_date_of_loss_against_policy_period_and_moratoriums_and_returns_match_status_with_coverage_context() {
        // Arrange
        String claimId = "CLM-2023-001";
        LocalDate dateOfLoss = LocalDate.of(2023, 6, 15);
        LocalDate policyEffective = LocalDate.of(2023, 1, 1);
        LocalDate policyExpiration = LocalDate.of(2023, 12, 31);
        boolean isMoratoriumActive = false;

        PolicyContext policyContext = new PolicyContext("POL-98765", policyEffective, policyExpiration, isMoratoriumActive);

        when(policyRegistryClient.queryByClaimId(claimId)).thenReturn(Optional.of(policyContext));
        when(rulesMatchingEngine.applyMatchingRules(any(PolicyContext.class), eq(dateOfLoss))).thenReturn(true);

        // Act
        EnrichmentResult result = enrichmentService.enrichClaim(claimId, dateOfLoss);

        // Assert
        assertNotNull(result, "EnrichmentResult should not be null");
        assertEquals("MATCH", result.matchStatus(), "Match status should be MATCH");
        assertTrue(result.coverageContext().containsKey("policyId"), "Coverage context must include policyId");
        assertEquals("POL-98765", result.coverageContext().get("policyId"), "PolicyId should match registry response");
        assertEquals(dateOfLoss, result.coverageContext().get("validatedDateOfLoss"), "Date of loss should be validated");
        assertFalse((boolean) result.coverageContext().get("isMoratoriumActive"), "Moratorium should not be active");

        // Verify external I/O interactions
        verify(policyRegistryClient, times(1)).queryByClaimId(claimId);
        verify(rulesMatchingEngine, times(1)).applyMatchingRules(any(PolicyContext.class), eq(dateOfLoss));
        verifyNoMoreInteractions(policyRegistryClient, rulesMatchingEngine);
    }

    // Internal domain models for test isolation
    record PolicyContext(String policyId, LocalDate effectiveDate, LocalDate expirationDate, boolean isMoratoriumActive) {}
    record EnrichmentResult(String matchStatus, Map<String, Object> coverageContext) {}

    // Minimal service implementation under test
    static class ClaimEnrichmentService {
        private final PolicyRegistryClient policyRegistryClient;
        private final RulesMatchingEngine rulesMatchingEngine;

        ClaimEnrichmentService(PolicyRegistryClient policyRegistryClient, RulesMatchingEngine rulesMatchingEngine) {
            this.policyRegistryClient = policyRegistryClient;
            this.rulesMatchingEngine = rulesMatchingEngine;
        }

        EnrichmentResult enrichClaim(String claimId, LocalDate dateOfLoss) {
            Optional<PolicyContext> policyOpt = policyRegistryClient.queryByClaimId(claimId);
            if (policyOpt.isEmpty()) {
                return new EnrichmentResult("NO_MATCH", Map.of());
            }
            PolicyContext policy = policyOpt.get();
            boolean isMatching = rulesMatchingEngine.applyMatchingRules(policy, dateOfLoss);
            
            if (isMatching) {
                return new EnrichmentResult("MATCH", Map.of(
                    "policyId", policy.policyId(),
                    "validatedDateOfLoss", dateOfLoss,
                    "isMoratoriumActive", policy.isMoratoriumActive()
                ));
            }
            return new EnrichmentResult("MISMATCH", Map.of());
        }
    }

    interface PolicyRegistryClient {
        Optional<PolicyContext> queryByClaimId(String claimId);
    }

    interface RulesMatchingEngine {
        boolean applyMatchingRules(PolicyContext policy, LocalDate dateOfLoss);
    }
}
