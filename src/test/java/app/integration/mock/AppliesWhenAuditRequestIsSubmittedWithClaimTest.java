package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

/**
 * Mock integration test for Insured Engagement & Tracking: decision: transformation.
 * Verifies behavior when external dependencies are mocked.
 */
@ExtendWith(MockitoExtension.class)
public class AppliesWhenAuditRequestIsSubmittedWithClaimTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private DecisionTransformationEngine decisionTransformationEngine;

    @Mock
    private AuditRequestValidator auditRequestValidator;

    @Mock
    private ComplianceDiaryService complianceDiaryService;

    @InjectMocks
    private InsuredEngagementService insuredEngagementService;

    private AuditRequest auditRequest;
    private Claim mockClaim;

    @BeforeEach
    void setUp() {
        // Initialize common test structures
        String fnolRef = "FNOL-2024-INT-001";
        auditRequest = new AuditRequest.Builder()
                .withFnolReference(fnolRef)
                .withAuditType(AuditType.COMPLIANCE)
                .withSubmittedBy("system-audit-bot")
                .build();

        mockClaim = new Claim(fnolRef);
        mockClaim.setClaimId("CLM-MOCK-9876");
        mockClaim.setStatus(ClaimStatus.OPEN);
        
        // Attach linked entities to simulate rich claim context
        ReserveLine reserve = new ReserveLine("RES-MOCK-001", fnolRef, 5000.00, "USD", ApprovalStatus.PENDING);
        mockClaim.setReserveLines(List.of(reserve));
    }

    @Test
    @DisplayName("Applies when audit request is submitted with claim/FNOL reference")
    void applies_when_audit_request_is_submitted_with_claim_fnol_reference() {
        // Given: Audit request contains a valid FNOL reference that maps to a claim
        when(claimRepository.findByFnolReference(auditRequest.getFnolReference()))
                .thenReturn(Optional.of(mockClaim));

        when(auditRequestValidator.validate(auditRequest)).thenReturn(ValidationResult.success());

        // When: Service processes the audit request
        TransformationResult result = insuredEngagementService.processAuditRequest(auditRequest);

        // Then: Transformation is applied, claim is linked, and persistence is triggered
        assertNotNull(result, "Transformation result should not be null");
        assertTrue(result.isTransformationApplied(), "Decision transformation should be applied");
        assertEquals(TransformationOutcome.AUDIT_LINKED, result.getOutcome());

        // Verify decision engine interaction
        verify(decisionTransformationEngine, times(1))
                .applyDecision(mockClaim, auditRequest);

        // Verify claim repository interaction (DynamoDB mock)
        verify(claimRepository, times(1))
                .save(eq(mockClaim));
        
        verify(claimRepository, times(1))
                .updateEngagementStatus(eq(auditRequest.getFnolReference()), any());

        // Verify compliance diary logging (NFR: Compliance/Operability)
        verify(complianceDiaryService, times(1))
                .logEvent(eq("AUDIT_REQUEST_SUBMITTED"), eq(mockClaim.getClaimId()), any());

        // Verify no SES email is sent for this specific internal audit flow (NFR: Security/Least Privilege)
        // Assuming email is only sent for external insured comms
        // verifyNoInteractions(emailService); // Optional if email service exists
    }
}
