package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * Integration mock tests for Multi-Channel FNOL Submission orchestration validation.
 * Verifies policy lookup, date-of-loss validation, product/form compatibility, and match confidence calculation.
 */
@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionOrchestrationValidationTest {

    @Mock
    private PolicyLookupService policyLookupService;

    @Mock
    private DateOfLossValidator dateOfLossValidator;

    @Mock
    private ProductFormCompatibilityChecker compatibilityChecker;

    @Mock
    private MatchConfidenceCalculator matchConfidenceCalculator;

    @Mock
    private StateTransitionService stateTransitionService;

    @InjectMocks
    private MultiChannelFnolOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // Mocks are initialized by MockitoExtension
    }

    @Test
    void description_orchestrates_policy_lookup_validates_date_of_loss_checks_product_form_compatibility_and_determines_match_confidence() {
        // Arrange
        String submissionId = "SUB-1001";
        String policyId = "POL-999";
        ZonedDateTime dateOfLoss = ZonedDateTime.now().minus(10, ChronoUnit.DAYS);
        
        Map<String, Object> payload = Map.of(
            "submissionId", submissionId,
            "policyId", policyId,
            "dateOfLoss", dateOfLoss.toString(),
            "productCode", "AUTO_LIABILITY",
            "formCode", "FORM_A",
            "claimantId", "CLM-500"
        );

        Policy mockPolicy = new Policy(
            policyId, 
            "AUTO_LIABILITY", 
            "FORM_A", 
            ZonedDateTime.now().minusYears(1), 
            ZonedDateTime.now().plusYears(1)
        );

        // Mock external dependencies
        when(policyLookupService.lookup(policyId)).thenReturn(Optional.of(mockPolicy));
        when(dateOfLossValidator.validate(dateOfLoss, mockPolicy)).thenReturn(true);
        when(compatibilityChecker.check(mockPolicy, payload)).thenReturn(true);
        when(matchConfidenceCalculator.calculate(mockPolicy, payload)).thenReturn(0.92);

        // Act
        OrchestrationResult result = orchestrationService.validateAndOrchestrate(submissionId, payload);

        // Assert: Verify orchestration steps were called in order
        verify(policyLookupService).lookup(policyId);
        verify(dateOfLossValidator).validate(dateOfLoss, mockPolicy);
        verify(compatibilityChecker).check(mockPolicy, payload);
        verify(matchConfidenceCalculator).calculate(mockPolicy, payload);

        // Assert: Verify result integrity
        assertNotNull(result, "Orchestration result should not be null");
        assertTrue(result.isValid(), "Result should be valid when all checks pass");
        assertEquals(0.92, result.matchConfidence(), "Match confidence should match calculated value");
        assertEquals(submissionId, result.submissionId(), "Submission ID should be preserved");

        // Assert: Verify state transition triggered with correct attributes
        verify(stateTransitionService).transition(
            eq(submissionId), 
            argThat(attrs -> 
                attrs.containsKey("matchConfidence") && 
                Double.compare((Double) attrs.get("matchConfidence"), 0.92) == 0 &&
                attrs.containsKey("validationStatus") &&
                "VALID".equals(attrs.get("validationStatus"))
            )
        );
    }

    // Minimal domain models for compilation context
    record Policy(String id, String productCode, String formCode, ZonedDateTime startDate, ZonedDateTime endDate) {}
    record OrchestrationResult(String submissionId, boolean valid, double matchConfidence) {}
}
