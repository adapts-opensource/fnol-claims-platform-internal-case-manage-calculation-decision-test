package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Mock integration test for Insured Engagement & Tracking: decision: transformation.
 * Verifies that the decision transformation logic is correctly triggered
 * when FNOL intake or policy match completion events occur.
 */
@ExtendWith(MockitoExtension.class)
class InsuredEngagementDecisionTransformationMockTest {

    @Mock
    private FnolIntakeService fnolIntakeService;

    @Mock
    private PolicyMatchService policyMatchService;

    @InjectMocks
    private InsuredEngagementDecisionTransformationService decisionTransformationService;

    @Test
    void appliesWhenFnolIntakeCompletes() {
        // Given: FNOL intake process has reached completion status
        when(fnolIntakeService.isIntakeComplete()).thenReturn(true);

        // When: Evaluating whether decision transformation applies
        boolean isApplicable = decisionTransformationService.isTransformationApplicable();

        // Then: Transformation logic should trigger upon FNOL intake completion
        assertTrue(isApplicable,
                "Decision transformation should apply when FNOL intake completes");
    }

    @Test
    void appliesWhenPolicyMatchCompletes() {
        // Given: Policy matching process has reached completion status
        when(policyMatchService.isPolicyMatchComplete()).thenReturn(true);

        // When: Evaluating whether decision transformation applies
        boolean isApplicable = decisionTransformationService.isTransformationApplicable();

        // Then: Transformation logic should trigger upon policy match completion
        assertTrue(isApplicable,
                "Decision transformation should apply when policy match completes");
    }
}
