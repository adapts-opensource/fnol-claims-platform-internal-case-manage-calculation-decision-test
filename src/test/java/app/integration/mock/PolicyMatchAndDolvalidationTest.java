package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyMatchAndDolValidationTest {

    @Mock
    private PolicyLookupService policyLookupService;
    @Mock
    private DateOfLossValidationService dateOfLossValidationService;
    @Mock
    private ClaimCaptureService claimCaptureService;
    @Mock
    private TaskCreationService taskCreationService;

    @InjectMocks
    private OrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        // Mocks are initialized automatically by MockitoExtension
    }

    @Test
    void orchestratePolicyMatchAndDateOfLossValidation() {
        // Arrange
        String policyNumber = "POL-12345";
        String riskAddress = "123 Main St";
        LocalDate dateOfLoss = LocalDate.of(2023, 12, 1);
        LocalDate policyEffective = LocalDate.of(2023, 1, 1);
        LocalDate policyExpiration = LocalDate.of(2023, 11, 30);
        String causeOfLoss = "wind";

        Policy policy = new Policy(policyNumber, policyEffective, policyExpiration);
        when(policyLookupService.findByPolicyNumber(policyNumber)).thenReturn(Optional.of(policy));

        DolValidationResult dolResult = new DolValidationResult("outside_policy_period", "coverage_uncertainty");
        when(dateOfLossValidationService.validate(dateOfLoss, policyEffective, policyExpiration)).thenReturn(dolResult);

        when(claimCaptureService.captureClaim(anyString(), anyString(), anyString())).thenReturn(true);
        when(taskCreationService.createCoverageReviewTask(anyString(), anyString())).thenReturn(true);

        // Act
        OrchestrationOutcome outcome = orchestrationService.process(policyNumber, riskAddress, dateOfLoss, causeOfLoss);

        // Assert expected results
        assertNotNull(outcome);
        assertTrue(outcome.isPolicyMatched(), "Policy should be matched successfully");
        assertTrue(outcome.isClaimCaptured(), "Claim should be captured");
        assertTrue(outcome.isCoverageReviewTaskCreated(), "Coverage review task should be created");
        assertEquals("outside_policy_period", outcome.getDateOfLossValidationStatus(), "DOL validation status should be outside_policy_period");
        assertEquals("coverage_uncertainty", outcome.getTriageDimension(), "Triage dimension should be coverage_uncertainty");

        // Verify external interactions
        verify(policyLookupService).findByPolicyNumber(policyNumber);
        verify(dateOfLossValidationService).validate(dateOfLoss, policyEffective, policyExpiration);
        verify(claimCaptureService).captureClaim(eq(policyNumber), eq(riskAddress), eq(causeOfLoss));
        verify(taskCreationService).createCoverageReviewTask(eq(policyNumber), eq("coverage_uncertainty"));
    }

    // Minimal supporting types for test compilation
    static class Policy {
        private final String policyNumber;
        private final LocalDate effectiveDate;
        private final LocalDate expirationDate;
        Policy(String policyNumber, LocalDate effectiveDate, LocalDate expirationDate) {
            this.policyNumber = policyNumber;
            this.effectiveDate = effectiveDate;
            this.expirationDate = expirationDate;
        }
    }

    static class DolValidationResult {
        private final String status;
        private final String triageDimension;
        DolValidationResult(String status, String triageDimension) {
            this.status = status;
            this.triageDimension = triageDimension;
        }
        String getStatus() { return status; }
        String getTriageDimension() { return triageDimension; }
    }

    static class OrchestrationOutcome {
        private boolean policyMatched;
        private boolean claimCaptured;
        private boolean coverageReviewTaskCreated;
        private String dateOfLossValidationStatus;
        private String triageDimension;

        boolean isPolicyMatched() { return policyMatched; }
        boolean isClaimCaptured() { return claimCaptured; }
        boolean isCoverageReviewTaskCreated() { return coverageReviewTaskCreated; }
        String getDateOfLossValidationStatus() { return dateOfLossValidationStatus; }
        String getTriageDimension() { return triageDimension; }
    }

    // Mocked service interfaces
    interface PolicyLookupService { Optional<Policy> findByPolicyNumber(String policyNumber); }
    interface DateOfLossValidationService { DolValidationResult validate(LocalDate dateOfLoss, LocalDate effective, LocalDate expiration); }
    interface ClaimCaptureService { boolean captureClaim(String policyNumber, String riskAddress, String causeOfLoss); }
    interface TaskCreationService { boolean createCoverageReviewTask(String policyNumber, String triageDimension); }
    interface OrchestrationService { OrchestrationOutcome process(String policyNumber, String riskAddress, LocalDate dateOfLoss, String causeOfLoss); }
}
