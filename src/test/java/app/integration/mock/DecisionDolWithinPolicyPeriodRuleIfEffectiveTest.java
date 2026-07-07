package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionDolWithinPolicyPeriodRuleIfEffectiveTest {

    @Mock
    private PolicyService policyService;

    @Test
    void decision_dol_within_policy_period_rule_if_effective_dol_expiration_and_not_cancelled_valid_else_coverage_review_expected_outcome_coverage_review_flag_and_manual_routing() {
        // Arrange
        String policyId = "POL-TEST-001";
        LocalDate effectiveDate = LocalDate.of(2023, 1, 1);
        LocalDate expirationDate = LocalDate.of(2024, 12, 31);
        LocalDate dateOfLoss = LocalDate.of(2023, 6, 15);

        when(policyService.getEffectiveDate(policyId)).thenReturn(effectiveDate);
        when(policyService.getExpirationDate(policyId)).thenReturn(expirationDate);
        when(policyService.isCancelled(policyId)).thenReturn(true);

        // Act
        ValidationOutcome outcome = new DolPolicyPeriodRule().evaluate(policyId, dateOfLoss, policyService);

        // Assert
        assertEquals(ValidationOutcome.COVERAGE_REVIEW, outcome);
        assertTrue(outcome.isFlagForManualRouting());
        assertFalse(outcome.isValid());
    }

    // Supporting types for test compilation
    enum ValidationOutcome {
        VALID, COVERAGE_REVIEW;
        public boolean isValid() { return this == VALID; }
        public boolean isFlagForManualRouting() { return this == COVERAGE_REVIEW; }
    }

    interface PolicyService {
        LocalDate getEffectiveDate(String policyId);
        LocalDate getExpirationDate(String policyId);
        boolean isCancelled(String policyId);
    }

    static class DolPolicyPeriodRule {
        ValidationOutcome evaluate(String policyId, LocalDate dol, PolicyService service) {
            LocalDate eff = service.getEffectiveDate(policyId);
            LocalDate exp = service.getExpirationDate(policyId);
            boolean cancelled = service.isCancelled(policyId);

            boolean withinPeriod = !cancelled && !dol.isBefore(eff) && !dol.isAfter(exp);
            return withinPeriod ? ValidationOutcome.VALID : ValidationOutcome.COVERAGE_REVIEW;
        }
    }
}
