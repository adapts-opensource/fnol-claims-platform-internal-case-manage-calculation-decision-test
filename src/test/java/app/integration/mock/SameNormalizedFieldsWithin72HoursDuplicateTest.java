package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SameNormalizedFieldsWithin72HoursDuplicateTest {

    @Mock
    private FnolDuplicateCheckRepository duplicateCheckRepository;

    private FnolValidationDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new FnolValidationDecisionService(duplicateCheckRepository);
    }

    @Test
    void same_normalized_fields_within_72_hours_duplicate() {
        // Arrange
        String normalizedPolicyId = "POL-987654";
        String normalizedClaimantId = "CLM-112233";
        LocalDateTime lossTimestamp = LocalDateTime.now().minusHours(24);
        LocalDateTime submissionTimestamp = LocalDateTime.now();

        // Simulate an existing claim found within the 72-hour window
        Claim existingClaim = new Claim(
                "CLM-HIST-001",
                normalizedPolicyId,
                normalizedClaimantId,
                lossTimestamp,
                submissionTimestamp.minusHours(12)
        );

        when(duplicateCheckRepository.findMatchingClaimWithinWindow(
                eq(normalizedPolicyId),
                eq(normalizedClaimantId),
                eq(lossTimestamp),
                eq(submissionTimestamp),
                eq(72)
        )).thenReturn(Optional.of(existingClaim));

        // Act
        DecisionResult result = decisionService.evaluateDuplicateSubmission(
                normalizedPolicyId,
                normalizedClaimantId,
                lossTimestamp,
                submissionTimestamp
        );

        // Assert
        assertNotNull(result, "Decision result should not be null");
        assertTrue(result.isDuplicate(), "Submission should be flagged as duplicate");
        assertEquals("CLM-HIST-001", result.getMatchingClaimId(), "Should reference the matching historical claim");
        verify(duplicateCheckRepository, times(1)).findMatchingClaimWithinWindow(anyString(), anyString(), any(), any(), anyInt());
    }

    // Minimal domain models for compilation context
    record Claim(String claimId, String policyId, String claimantId, LocalDateTime lossDate, LocalDateTime submissionDate) {}
    record DecisionResult(boolean duplicate, String matchingClaimId) {}

    // Mocked interface representing external data persistence (e.g., DynamoDB/S3 integration layer)
    interface FnolDuplicateCheckRepository {
        Optional<Claim> findMatchingClaimWithinWindow(String policyId, String claimantId, LocalDateTime lossDate, LocalDateTime submissionDate, int windowHours);
    }

    // Service under test containing validation decision logic
    static class FnolValidationDecisionService {
        private final FnolDuplicateCheckRepository duplicateCheckRepository;

        FnolValidationDecisionService(FnolDuplicateCheckRepository duplicateCheckRepository) {
            this.duplicateCheckRepository = duplicateCheckRepository;
        }

        DecisionResult evaluateDuplicateSubmission(String policyId, String claimantId, LocalDateTime lossDate, LocalDateTime submissionDate) {
            Optional<Claim> match = duplicateCheckRepository.findMatchingClaimWithinWindow(policyId, claimantId, lossDate, submissionDate, 72);
            if (match.isPresent()) {
                return new DecisionResult(true, match.get().claimId());
            }
            return new DecisionResult(false, null);
        }
    }
}
