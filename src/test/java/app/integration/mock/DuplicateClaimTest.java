package app.integration.mock;

import app.model.claim.Claim;
import app.model.claim.ClaimInitiationRequest;
import app.model.claim.ClaimInitiationResult;
import app.model.task.Task;
import app.service.ClaimInitiationService;
import app.service.DuplicateDetectionService;
import app.service.TaskService;
import app.repository.ClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock test class for Claim Initiation & Routing:decision:validation feature.
 * Verifies duplicate claim detection logic, status updates, task creation, and linking.
 */
@ExtendWith(MockitoExtension.class)
class DuplicateClaimValidationTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private DuplicateDetectionService duplicateDetectionService;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private ClaimInitiationService claimInitiationService;

    @Test
    void validate_duplicate_claim_detection() {
        // Given: Test Inputs
        String policyNumber = "POL-400";
        String riskAddress = "123 Oak Ave";
        String lossDate = "2024-05-15";
        String causeOfLoss = "wind";
        String reporter = "Alice Smith";
        String existingClaimId = "CLM-FL01-2024-00001100";

        // Mock existing claim returned from infrastructure (DynamoDB/Redis)
        Claim existingClaim = new Claim();
        existingClaim.setId(existingClaimId);
        existingClaim.setPolicyNumber(policyNumber);
        existingClaim.setRiskAddress(riskAddress);
        existingClaim.setLossDate(lossDate);
        existingClaim.setCauseOfLoss(causeOfLoss);

        // Mock infrastructure I/O: Find matching claims
        when(claimRepository.findMatchingClaims(policyNumber, riskAddress, lossDate, causeOfLoss))
                .thenReturn(List.of(existingClaim));

        // Mock duplicate score calculation
        double duplicateScore = 0.95;
        when(duplicateDetectionService.calculateDuplicateScore(policyNumber, riskAddress, lossDate, causeOfLoss))
                .thenReturn(duplicateScore);

        // Mock task creation
        Task reviewTask = new Task("Review Potential Duplicate Claim", existingClaimId, reporter);
        when(taskService.createTask(eq("Review Potential Duplicate Claim"), eq(existingClaimId), eq(reporter)))
                .thenReturn(reviewTask);

        // When: Execute feature logic
        ClaimInitiationResult result = claimInitiationService.initiateAndValidate(
                policyNumber, riskAddress, lossDate, causeOfLoss, reporter
        );

        // Then: Verify Expected Results
        assertNotNull(result, "Result should not be null");

        // Verify Claim status set to Duplicate Review
        assertEquals("Duplicate Review", result.getStatus(), "Claim status should be set to Duplicate Review");

        // Verify existing claim linked for comparison
        assertNotNull(result.getLinkedClaimId(), "Existing claim should be linked for comparison");
        assertEquals(existingClaimId, result.getLinkedClaimId(), "Linked claim ID should match existing claim");

        // Verify duplicate score calculated
        assertNotNull(result.getDuplicateScore(), "Duplicate score should be calculated");
        assertEquals(duplicateScore, result.getDuplicateScore(), "Duplicate score should match calculation");

        // Verify Task created
        verify(taskService, times(1))
                .createTask("Review Potential Duplicate Claim", existingClaimId, reporter);

        // Verify infrastructure interactions
        verify(claimRepository, times(1))
                .findMatchingClaims(policyNumber, riskAddress, lossDate, causeOfLoss);
        
        verify(duplicateDetectionService, times(1))
                .calculateDuplicateScore(policyNumber, riskAddress, lossDate, causeOfLoss);
    }
}
