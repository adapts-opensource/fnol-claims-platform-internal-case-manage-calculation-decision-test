package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;

@ExtendWith(MockitoExtension.class)
public class LossOutsidePolicyPeriodFlagsCoverageReviewTest {

    @Mock
    private PolicyPeriodValidator policyPeriodValidator;

    @Mock
    private DecisionTransformer decisionTransformer;

    @Mock
    private CoverageReviewDispatcher coverageReviewDispatcher;

    private LossTransformationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new LossTransformationEngine(policyPeriodValidator, decisionTransformer, coverageReviewDispatcher);
    }

    @Test
    void loss_outside_policy_period_flags_coverage_review() {
        // Arrange
        String lossId = "LOSS-2024-OUTSIDE-POLICY";
        LocalDate lossDate = LocalDate.of(2024, 5, 10);
        LocalDate policyStart = LocalDate.of(2024, 6, 1);
        LocalDate policyEnd = LocalDate.of(2024, 11, 30);

        when(policyPeriodValidator.isWithinActivePeriod(lossId, lossDate)).thenReturn(false);

        // Act
        boolean isFlagged = engine.processLossForCoverageReview(lossId, lossDate, policyStart, policyEnd);

        // Assert
        assertTrue(isFlagged, "Loss outside policy period should trigger coverage review flag");
        verify(decisionTransformer).applyTransformationRule(eq(lossId), eq("OUTSIDE_POLICY_PERIOD"));
        verify(coverageReviewDispatcher).queueForReview(lossId, "Coverage review required: loss outside active policy period");
    }

    // Minimal mockable interfaces and production-like engine for demonstration
    interface PolicyPeriodValidator {
        boolean isWithinActivePeriod(String lossId, LocalDate lossDate);
    }

    interface DecisionTransformer {
        void applyTransformationRule(String lossId, String ruleCode);
    }

    interface CoverageReviewDispatcher {
        void queueForReview(String lossId, String reason);
    }

    static class LossTransformationEngine {
        private final PolicyPeriodValidator validator;
        private final DecisionTransformer transformer;
        private final CoverageReviewDispatcher dispatcher;

        LossTransformationEngine(PolicyPeriodValidator validator, DecisionTransformer transformer, CoverageReviewDispatcher dispatcher) {
            this.validator = validator;
            this.transformer = transformer;
            this.dispatcher = dispatcher;
        }

        boolean processLossForCoverageReview(String lossId, LocalDate lossDate, LocalDate policyStart, LocalDate policyEnd) {
            if (!validator.isWithinActivePeriod(lossId, lossDate)) {
                transformer.applyTransformationRule(lossId, "OUTSIDE_POLICY_PERIOD");
                dispatcher.queueForReview(lossId, "Coverage review required: loss outside active policy period");
                return true;
            }
            return false;
        }
    }
}
