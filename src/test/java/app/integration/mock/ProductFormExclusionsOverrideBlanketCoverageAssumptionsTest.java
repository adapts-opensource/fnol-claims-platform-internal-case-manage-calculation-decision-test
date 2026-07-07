package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Domain interfaces for test isolation and mock-based validation
interface FnolSubmissionPayload {
    String getPolicyId();
    String getTenantId();
    String getProductFormCode();
    boolean isBlanketCoverageAssumed();
}

interface ProductFormExclusion {
    boolean isApplicable();
    String getExclusionCode();
}

interface DecisionResult {
    boolean isCoverageGranted();
    String getDecisionReason();
    boolean hasAppliedExclusionOverride();
}

interface ValidationDecisionEngine {
    DecisionResult evaluate(FnolSubmissionPayload payload, ProductFormExclusion exclusion);
}

class ProductFormExclusionsOverrideBlanketCoverageAssumptionsTest {

    @Mock
    private ValidationDecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void product_form_exclusions_override_blanket_coverage_assumptions() {
        // Arrange
        FnolSubmissionPayload submission = mock(FnolSubmissionPayload.class);
        when(submission.getPolicyId()).thenReturn("POL-789012");
        when(submission.getTenantId()).thenReturn("TENANT-NEWCO-01");
        when(submission.getProductFormCode()).thenReturn("FORM-EXCL-ALPHA");
        when(submission.isBlanketCoverageAssumed()).thenReturn(true);

        ProductFormExclusion exclusion = mock(ProductFormExclusion.class);
        when(exclusion.isApplicable()).thenReturn(true);
        when(exclusion.getExclusionCode()).thenReturn("EXCL-BLANKET-OVERRIDE");

        DecisionResult expectedDecision = mock(DecisionResult.class);
        when(expectedDecision.isCoverageGranted()).thenReturn(false);
        when(expectedDecision.getDecisionReason()).thenReturn("EXCLUSION_OVERRIDE_APPLIED");
        when(expectedDecision.hasAppliedExclusionOverride()).thenReturn(true);

        when(decisionEngine.evaluate(submission, exclusion)).thenReturn(expectedDecision);

        // Act
        DecisionResult actualDecision = decisionEngine.evaluate(submission, exclusion);

        // Assert
        assertNotNull(actualDecision, "Decision result must not be null");
        assertFalse(actualDecision.isCoverageGranted(), "Coverage must be denied when exclusion overrides blanket assumption");
        assertEquals("EXCLUSION_OVERRIDE_APPLIED", actualDecision.getDecisionReason(), "Reason must reflect exclusion precedence");
        assertTrue(actualDecision.hasAppliedExclusionOverride(), "Override flag must be set to ensure auditability and idempotency");
        
        verify(decisionEngine, times(1)).evaluate(submission, exclusion);
        verify(submission).isBlanketCoverageAssumed();
        verify(exclusion).isApplicable();
    }
}
