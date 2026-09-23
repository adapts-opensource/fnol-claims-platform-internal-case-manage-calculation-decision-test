package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that during insured engagement claim merging, an OPEN claim 
 * takes precedence over a CLOSED claim for decision transformation.
 * Aligns with SOC2/GDPR data handling boundaries by isolating logic behind mocked I/O.
 */
@ExtendWith(MockitoExtension.class)
class OpenClaimTakesPrecedenceOverClosedForMergeTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private DecisionTransformationEngine transformationEngine;

    private InsuredEngagementProcessor engagementProcessor;

    @BeforeEach
    void setUp() {
        // Dependency injection with mocked I/O to prevent live AWS/HTTP calls
        engagementProcessor = new InsuredEngagementProcessor(claimRepository, transformationEngine);
    }

    @Test
    void openClaimTakesPrecedenceOverClosedForMerge() {
        // Arrange
        String openClaimId = "CLM-OPEN-001";
        String closedClaimId = "CLM-CLOSED-002";
        Claim openClaim = new Claim(openClaimId, ClaimStatus.OPEN);
        Claim closedClaim = new Claim(closedClaimId, ClaimStatus.CLOSED);

        when(claimRepository.findById(openClaimId)).thenReturn(Optional.of(openClaim));
        when(claimRepository.findById(closedClaimId)).thenReturn(Optional.of(closedClaim));

        List<String> mergeCandidates = List.of(openClaimId, closedClaimId);

        // Act
        MergeResult result = engagementProcessor.evaluateMergePrecedence(mergeCandidates);

        // Assert
        assertNotNull(result, "Merge decision result must not be null");
        assertEquals(openClaimId, result.primaryClaimId(), "Open claim ID must be selected as primary");
        assertEquals(ClaimStatus.OPEN, result.primaryStatus(), "Primary status must reflect OPEN state");
        assertTrue(result.isOpenClaimPrecedent(), "Open claim must take precedence in merge decision");
        assertFalse(result.isClosedClaimPrecedent(), "Closed claim must not override open status");

        // Verify transformation engine was invoked exactly once with the resolved precedence
        verify(transformationEngine, times(1)).transform(eq(mergeCandidates), eq(result));
    }

    // Minimal domain contracts for test compilation
    record Claim(String id, ClaimStatus status) {}
    record MergeResult(String primaryClaimId, ClaimStatus primaryStatus, boolean isOpenClaimPrecedent, boolean isClosedClaimPrecedent) {}

    interface ClaimRepository {
        Optional<Claim> findById(String claimId);
    }

    interface DecisionTransformationEngine {
        void transform(List<String> claimIds, MergeResult decision);
    }

    class InsuredEngagementProcessor {
        private final ClaimRepository claimRepository;
        private final DecisionTransformationEngine transformationEngine;

        InsuredEngagementProcessor(ClaimRepository claimRepository, DecisionTransformationEngine transformationEngine) {
            this.claimRepository = claimRepository;
            this.transformationEngine = transformationEngine;
        }

        MergeResult evaluateMergePrecedence(List<String> claimIds) {
            Claim primary = null;
            for (String id : claimIds) {
                Optional<Claim> opt = claimRepository.findById(id);
                if (opt.isPresent()) {
                    Claim current = opt.get();
                    // Open claim takes precedence over closed
                    if (primary == null || (primary.status() == ClaimStatus.CLOSED && current.status() == ClaimStatus.OPEN)) {
                        primary = current;
                    }
                }
            }
            if (primary == null) {
                throw new IllegalStateException("No valid claims found for merge decision");
            }
            boolean isOpenPrecedent = primary.status() == ClaimStatus.OPEN;
            return new MergeResult(primary.id(), primary.status(), isOpenPrecedent, !isOpenPrecedent);
        }
    }
}
