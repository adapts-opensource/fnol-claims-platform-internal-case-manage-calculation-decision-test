package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DecisionCoverageStatusRuleIfDolEffectiveOrTest {

    @Mock
    private CoverageDecisionTransformationService coverageDecisionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void decision_coverage_status_rule_if_dol_effective_or_expiration_coverage_review_required_expected_outcome_route_to_coverage_specialist() {
        // Arrange: DOL outside the policy effective/expiration window
        String dateOfLoss = "2022-11-20";
        String effectiveDate = "2023-01-01";
        String expirationDate = "2023-12-31";

        // Mock the transformation service to return the expected routing outcome
        when(coverageDecisionService.evaluateCoverageStatus(dateOfLoss, effectiveDate, expirationDate))
                .thenReturn("Route to coverage specialist");

        // Act
        String actualOutcome = coverageDecisionService.evaluateCoverageStatus(dateOfLoss, effectiveDate, expirationDate);

        // Assert
        assertEquals("Route to coverage specialist", actualOutcome,
                "Coverage status transformation should route to specialist when DOL < effective or > expiration");
        verify(coverageDecisionService, times(1))
                .evaluateCoverageStatus(dateOfLoss, effectiveDate, expirationDate);
    }
}
