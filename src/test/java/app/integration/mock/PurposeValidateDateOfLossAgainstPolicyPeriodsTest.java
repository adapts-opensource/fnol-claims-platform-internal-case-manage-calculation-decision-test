package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurposeValidateDateOfLossAgainstPolicyPeriodsTest {

    @Mock
    private PolicyPeriodValidator policyPeriodValidator;

    @Mock
    private RegulatoryConstraintChecker regulatoryConstraintChecker;

    @InjectMocks
    private OrchestrationDecisionEngine orchestrationDecisionEngine;

    @BeforeEach
    void setUp() {
        // Ensure mock state is clean before each test
    }

    @Test
    void purpose_validate_date_of_loss_against_policy_periods_and_regulatory_constraints_validPeriodAndCompliance_returnsContinue() {
        // arrange
        LocalDate dateOfLoss = LocalDate.of(2023, 6, 15);
        LocalDate effective = LocalDate.of(2023, 1, 1);
        LocalDate expiration = LocalDate.of(2023, 12, 31);
        String jurisdiction = "NY";

        when(policyPeriodValidator.isWithinPeriod(dateOfLoss, effective, expiration)).thenReturn(true);
        when(regulatoryConstraintChecker.isCompliant(dateOfLoss, jurisdiction)).thenReturn(true);

        // act
        DecisionResult result = orchestrationDecisionEngine.evaluate(dateOfLoss, effective, expiration, jurisdiction);

        // assert
        assertEquals(DecisionStatus.CONTINUE, result.getStatus());
        assertTrue(result.isEligibleForNextStep());
        verify(policyPeriodValidator, times(1)).isWithinPeriod(dateOfLoss, effective, expiration);
        verify(regulatoryConstraintChecker, times(1)).isCompliant(dateOfLoss, jurisdiction);
    }

    @Test
    void purpose_validate_date_of_loss_against_policy_periods_and_regulatory_constraints_beforeEffectiveDate_returnsReject() {
        // arrange
        LocalDate dateOfLoss = LocalDate.of(2022, 12, 31);
        LocalDate effective = LocalDate.of(2023, 1, 1);
        LocalDate expiration = LocalDate.of(2023, 12, 31);
        String jurisdiction = "NY";

        when(policyPeriodValidator.isWithinPeriod(dateOfLoss, effective, expiration)).thenReturn(false);
        when(regulatoryConstraintChecker.isCompliant(dateOfLoss, jurisdiction)).thenReturn(true);

        // act
        DecisionResult result = orchestrationDecisionEngine.evaluate(dateOfLoss, effective, expiration, jurisdiction);

        // assert
        assertEquals(DecisionStatus.REJECT, result.getStatus());
        assertFalse(result.isEligibleForNextStep());
        verifyNoInteractions(regulatoryConstraintChecker);
    }

    @Test
    void purpose_validate_date_of_loss_against_policy_periods_and_regulatory_constraints_afterExpirationDate_returnsReject() {
        // arrange
        LocalDate dateOfLoss = LocalDate.of(2024, 1, 15);
        LocalDate effective = LocalDate.of(2023, 1, 1);
        LocalDate expiration = LocalDate.of(2023, 12, 31);
        String jurisdiction = "NY";

        when(policyPeriodValidator.isWithinPeriod(dateOfLoss, effective, expiration)).thenReturn(false);
        when(regulatoryConstraintChecker.isCompliant(dateOfLoss, jurisdiction)).thenReturn(true);

        // act
        DecisionResult result = orchestrationDecisionEngine.evaluate(dateOfLoss, effective, expiration, jurisdiction);

        // assert
        assertEquals(DecisionStatus.REJECT, result.getStatus());
        verifyNoInteractions(regulatoryConstraintChecker);
    }

    @Test
    void purpose_validate_date_of_loss_against_policy_periods_and_regulatory_constraints_regulatoryViolation_returnsBlock() {
        // arrange
        LocalDate dateOfLoss = LocalDate.of(2023, 6, 15);
        LocalDate effective = LocalDate.of(2023, 1, 1);
        LocalDate expiration = LocalDate.of(2023, 12, 31);
        String jurisdiction = "NY";

        when(policyPeriodValidator.isWithinPeriod(dateOfLoss, effective, expiration)).thenReturn(true);
        when(regulatoryConstraintChecker.isCompliant(dateOfLoss, jurisdiction)).thenReturn(false);

        // act
        DecisionResult result = orchestrationDecisionEngine.evaluate(dateOfLoss, effective, expiration, jurisdiction);

        // assert
        assertEquals(DecisionStatus.BLOCK, result.getStatus());
        assertFalse(result.isEligibleForNextStep());
        assertTrue(result.getReasons().contains("REGULATORY_CONSTRAINT_VIOLATION"));
    }

    // Minimal domain & interface stubs for compilation context
    interface PolicyPeriodValidator { boolean isWithinPeriod(LocalDate dateOfLoss, LocalDate effective, LocalDate expiration); }
    interface RegulatoryConstraintChecker { boolean isCompliant(LocalDate dateOfLoss, String jurisdiction); }
    enum DecisionStatus { CONTINUE, REJECT, BLOCK }
    record DecisionResult(DecisionStatus status, boolean eligibleForNextStep, List<String> reasons) {
        static DecisionResult continueResult() { return new DecisionResult(DecisionStatus.CONTINUE, true, Collections.emptyList()); }
        static DecisionResult rejectResult() { return new DecisionResult(DecisionStatus.REJECT, false, Collections.emptyList()); }
        static DecisionResult blockResult(List<String> reasons) { return new DecisionResult(DecisionStatus.BLOCK, false, reasons); }
    }
    class OrchestrationDecisionEngine {
        private final PolicyPeriodValidator policyPeriodValidator;
        private final RegulatoryConstraintChecker regulatoryConstraintChecker;
        OrchestrationDecisionEngine(PolicyPeriodValidator p, RegulatoryConstraintChecker r) {
            this.policyPeriodValidator = p; this.regulatoryConstraintChecker = r;
        }
        DecisionResult evaluate(LocalDate dateOfLoss, LocalDate effective, LocalDate expiration, String jurisdiction) {
            if (!policyPeriodValidator.isWithinPeriod(dateOfLoss, effective, expiration)) {
                return DecisionResult.rejectResult();
            }
            if (!regulatoryConstraintChecker.isCompliant(dateOfLoss, jurisdiction)) {
                return DecisionResult.blockResult(List.of("REGULATORY_CONSTRAINT_VIOLATION"));
            }
            return DecisionResult.continueResult();
        }
    }
}
