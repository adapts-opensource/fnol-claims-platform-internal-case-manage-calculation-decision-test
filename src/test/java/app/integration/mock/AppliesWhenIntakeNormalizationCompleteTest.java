package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for Multi-Channel FNOL Submission validation decision logic.
 * Verifies behavior based on intake normalization state.
 */
@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionValidationDecisionTest {

    @Mock
    private IntakeNormalizationService intakeNormalizationService;

    @Mock
    private ClaimRepository claimRepository;

    @InjectMocks
    private FnolValidationDecisionService fnolValidationDecisionService;

    /**
     * Test: applies_when_intake_normalization_complete
     * Description: Applies when: Intake normalization complete
     * 
     * Verifies that the validation decision evaluates to APPLIES 
     * when the intake normalization process is marked as complete.
     */
    @Test
    void applies_when_intake_normalization_complete() {
        // Arrange
        String claimId = "claim-mock-001";
        String tenantId = "tenant-mock-001";
        Claim mockClaim = new Claim();
        mockClaim.setClaimId(claimId);
        mockClaim.setTenantId(tenantId);
        mockClaim.setPolicyId("policy-mock-001");
        mockClaim.setClaimNumber("FNOL-MOCK-001");

        // Mock normalization check to simulate completed state
        when(intakeNormalizationService.isNormalizationComplete(mockClaim)).thenReturn(true);

        // Act
        DecisionResult result = fnolValidationDecisionService.evaluate(mockClaim);

        // Assert
        assertNotNull(result, "Decision result should not be null");
        assertEquals(DecisionOutcome.APPLIES, result.getOutcome(), 
            "Decision should apply when intake normalization is complete");
        assertTrue(result.isPass(), "Validation should pass upon successful normalization");

        // Verify interactions
        verify(intakeNormalizationService).isNormalizationComplete(mockClaim);
        verifyNoInteractions(claimRepository, 
            "Repository should not be accessed during pure validation decision evaluation");
    }
}
