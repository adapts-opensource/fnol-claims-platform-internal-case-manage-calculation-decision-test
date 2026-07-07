package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for Insured Engagement & Tracking: Decision Transformation.
 * Verifies duplicate detection logic with mocked external I/O.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Insured Engagement & Tracking: Decision Transformation")
class InsuredEngagementTransformationTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private DuplicateDecisionService decisionService;

    @Mock
    private TransformationEngine transformationEngine;

    private InsuredEngagementTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new InsuredEngagementTransformer(claimRepository, decisionService, transformationEngine);
    }

    @Test
    @DisplayName("SamePolicySameDolSameCauseHighConfidence")
    void same_policy_same_dol_same_cause_high_confidence_duplicate() {
        // Arrange: Inputs matching existing claim
        String policyId = "POL-88492";
        String dateOfLoss = "2023-10-01";
        String causeCode = "WINDSTORM";

        Claim existingClaim = new Claim("CLM-EXIST-001", policyId, dateOfLoss, causeCode);
        
        // Mock external I/O: Database lookup returns a matching claim
        when(claimRepository.findByPolicyAndCause(policyId, causeCode))
            .thenReturn(List.of(existingClaim));
        
        // Mock external I/O: Transformation engine validates input
        when(transformationEngine.transform(any())).thenReturn(true);
        
        // Mock external I/O: Decision service returns high confidence match
        DuplicateDecision expectedDecision = new DuplicateDecision(DuplicateDecisionStatus.HIGH_CONFIDENCE, "CLM-EXIST-001");
        when(decisionService.evaluate(anyList())).thenReturn(expectedDecision);

        // Act: Process the transformation
        TransformationResult result = transformer.process(policyId, dateOfLoss, causeCode);

        // Assert: Verify high confidence duplicate decision
        assertNotNull(result, "Transformation result should not be null");
        assertEquals(DuplicateDecisionStatus.HIGH_CONFIDENCE, result.getDecisionStatus(),
            "Decision status should be HIGH_CONFIDENCE for matching policy, DOL, and cause");
        assertEquals("CLM-EXIST-001", result.getMatchedClaimId(),
            "Matched claim ID should be set for high confidence duplicate");
        assertTrue(result.isDuplicate(), "Result should be flagged as duplicate");

        // Verify interactions
        verify(claimRepository, times(1)).findByPolicyAndCause(policyId, causeCode);
        verify(transformationEngine, times(1)).transform(any());
        verify(decisionService, times(1)).evaluate(anyList());
    }

    // --- Package-private stubs and interfaces for self-contained test compilation ---

    interface ClaimRepository {
        List<Claim> findByPolicyAndCause(String policyId, String causeCode);
    }

    interface DuplicateDecisionService {
        DuplicateDecision evaluate(List<Claim> candidates);
    }

    interface TransformationEngine {
        boolean transform(List<Claim> candidates);
    }

    class InsuredEngagementTransformer {
        private final ClaimRepository claimRepository;
        private final DuplicateDecisionService decisionService;
        private final TransformationEngine transformationEngine;

        InsuredEngagementTransformer(ClaimRepository claimRepository, 
                                     DuplicateDecisionService decisionService, 
                                     TransformationEngine transformationEngine) {
            this.claimRepository = claimRepository;
            this.decisionService = decisionService;
            this.transformationEngine = transformationEngine;
        }

        TransformationResult process(String policyId, String dateOfLoss, String causeCode) {
            List<Claim> candidates = claimRepository.findByPolicyAndCause(policyId, causeCode);
            transformationEngine.transform(candidates);
            DuplicateDecision decision = decisionService.evaluate(candidates);
            return new TransformationResult(decision.getStatus(), decision.getMatchedClaimId(), 
                                            decision.getStatus() != DuplicateDecisionStatus.NO_MATCH);
        }
    }

    class Claim {
        private final String claimId;
        private final String policyId;
        private final String dateOfLoss;
        private final String cause;

        Claim(String claimId, String policyId, String dateOfLoss, String cause) {
            this.claimId = claimId;
            this.policyId = policyId;
            this.dateOfLoss = dateOfLoss;
            this.cause = cause;
        }

        String getClaimId() { return claimId; }
        String getPolicyId() { return policyId; }
        String getDateOfLoss() { return dateOfLoss; }
        String getCause() { return cause; }
    }

    class DuplicateDecision {
        private final DuplicateDecisionStatus status;
        private final String matchedClaimId;

        DuplicateDecision(DuplicateDecisionStatus status, String matchedClaimId) {
            this.status = status;
            this.matchedClaimId = matchedClaimId;
        }

        DuplicateDecisionStatus getStatus() { return status; }
        String getMatchedClaimId() { return matchedClaimId; }
    }

    class TransformationResult {
        private final DuplicateDecisionStatus decisionStatus;
        private final String matchedClaimId;
        private final boolean isDuplicate;

        TransformationResult(DuplicateDecisionStatus decisionStatus, String matchedClaimId, boolean isDuplicate) {
            this.decisionStatus = decisionStatus;
            this.matchedClaimId = matchedClaimId;
            this.isDuplicate = isDuplicate;
        }

        DuplicateDecisionStatus getDecisionStatus() { return decisionStatus; }
        String getMatchedClaimId() { return matchedClaimId; }
        boolean isDuplicate() { return isDuplicate; }
    }

    enum DuplicateDecisionStatus {
        HIGH_CONFIDENCE,
        LOW_CONFIDENCE,
        NO_MATCH
    }
}
