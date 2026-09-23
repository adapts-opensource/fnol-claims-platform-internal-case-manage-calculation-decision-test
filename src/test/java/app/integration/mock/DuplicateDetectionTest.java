package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DuplicateDetectionTest {

    @Mock
    private ClaimLookupService claimLookupService;
    @Mock
    private TaskCreationService taskCreationService;
    @Mock
    private AuditLoggingService auditLoggingService;

    private DuplicateDetectionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new DuplicateDetectionOrchestrator(claimLookupService, taskCreationService, auditLoggingService);
    }

    @Test
    void orchestrate_duplicate_claim_detection() {
        // Arrange
        String channel = "Internal CSR";
        String policyNumber = "FL-HO3-11111";
        String address = "456 Ocean Dr";
        LocalDate dateOfLoss = LocalDate.of(2024, 6, 1);
        String cause = "Water";
        String reporter = "ClaimsIntake";
        String existingClaimId = "EXIST-CLM-98765";

        // Mock duplicate detection: policy, address, and loss date match
        when(claimLookupService.findMatchingClaim(policyNumber, address, dateOfLoss))
            .thenReturn(Optional.of(existingClaimId));

        // Act
        OrchestrationOutcome outcome = orchestrator.processFnolSubmission(
            channel, policyNumber, address, dateOfLoss, cause, reporter
        );

        // Assert expected results
        assertEquals("Duplicate Review", outcome.getState());
        assertNull(outcome.getNewClaimNumber(), "No new claim number should be generated for duplicates");
        assertEquals(existingClaimId, outcome.getReferencedClaimId());

        // Verify task creation
        verify(taskCreationService).createReviewTask(
            "Review Potential Duplicate Claim",
            existingClaimId,
            reporter
        );

        // Verify audit log records duplicate check result
        verify(auditLoggingService).recordAuditEvent(
            eq("DUPLICATE_CHECK"),
            argThat(args ->
                args.get("policyNumber").equals(policyNumber) &&
                args.get("address").equals(address) &&
                args.get("dateOfLoss").equals(dateOfLoss.toString()) &&
                args.get("detectionResult").equals("POTENTIAL_DUPLICATE")
            )
        );
    }
}
