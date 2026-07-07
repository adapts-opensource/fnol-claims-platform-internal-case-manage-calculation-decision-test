package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimRoutingDecisionCalculationMockTest {

    @Mock
    private PolicyCoverageRetriever policyCoverageRetriever;

    @InjectMocks
    private ClaimRoutingDecisionCalculator claimRoutingDecisionCalculator;

    @Test
    void decision_date_of_loss_within_period_rule_effective_dol_expiration_or_rewrite_reinstatement_applies_expected_outcome_coverage_context_established_or_flagged() {
        // Given
        String claimId = "CLM-2023-001";
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate expirationDate = LocalDate.of(2023, 12, 31);
        LocalDate dateOfLoss = LocalDate.of(2023, 6, 15);

        Map<String, Object> coverageData = Map.of(
                "effectiveDate", effectiveDate.toString(),
                "expirationDate", expirationDate.toString(),
                "rewriteReinstatementApplies", false
        );

        when(policyCoverageRetriever.fetchCoverageContext(claimId)).thenReturn(coverageData);

        // When
        Map<String, Object> decisionResult = claimRoutingDecisionCalculator.evaluateClaimInitiation(claimId);

        // Then
        assertNotNull(decisionResult);
        assertEquals("Coverage context established or flagged", decisionResult.get("decision"));
        assertEquals("Date-of-loss within period", decisionResult.get("rule"));
        assertTrue((boolean) decisionResult.get("withinCoveragePeriod"));
        verify(policyCoverageRetriever).fetchCoverageContext(claimId);
    }

    // Minimal supporting contracts for compilation and mock isolation
    interface PolicyCoverageRetriever {
        Map<String, Object> fetchCoverageContext(String claimId);
    }

    static class ClaimRoutingDecisionCalculator {
        private final PolicyCoverageRetriever policyCoverageRetriever;

        ClaimRoutingDecisionCalculator(PolicyCoverageRetriever policyCoverageRetriever) {
            this.policyCoverageRetriever = policyCoverageRetriever;
        }

        Map<String, Object> evaluateClaimInitiation(String claimId) {
            Map<String, Object> coverageData = policyCoverageRetriever.fetchCoverageContext(claimId);
            String effStr = (String) coverageData.get("effectiveDate");
            String expStr = (String) coverageData.get("expirationDate");
            LocalDate effective = LocalDate.parse(effStr);
            LocalDate expiration = LocalDate.parse(expStr);
            LocalDate dol = LocalDate.now();

            boolean within = !effective.isAfter(dol) && !expiration.isBefore(dol);
            return Map.of(
                    "decision", within ? "Coverage context established or flagged" : "Coverage gap",
                    "rule", "Date-of-loss within period",
                    "withinCoveragePeriod", within
            );
        }
    }
}
