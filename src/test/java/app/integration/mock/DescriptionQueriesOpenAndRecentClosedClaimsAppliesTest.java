package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionOrchestrationMockTest {

    @Mock
    private ClaimQueryService claimQueryService;
    @Mock
    private FuzzyMatchingService fuzzyMatchingService;
    @Mock
    private TaskCreationService taskCreationService;

    private DecisionOrchestrator decisionOrchestrator;
    private static final double REVIEW_THRESHOLD = 0.85;

    @BeforeEach
    void setUp() {
        decisionOrchestrator = new DecisionOrchestrator(
                claimQueryService,
                fuzzyMatchingService,
                taskCreationService,
                REVIEW_THRESHOLD
        );
    }

    @Test
    void description_queries_open_and_recent_closed_claims_applies_fuzzy_matching_on_address_and_cause_scores_potential_duplicates_creates_task_for_review_if_threshold_met() {
        // Arrange
        String claimId = "claim-init-001";
        Map<String, Object> newClaimPayload = Map.of(
                "address", "123 Main St, Springfield, IL",
                "cause", "vehicle collision"
        );

        List<Map<String, Object>> existingClaims = List.of(
                Map.of("id", "claim-001", "address", "123 Main St, Springfield, IL", "cause", "car crash"),
                Map.of("id", "claim-002", "address", "456 Oak Ave, Shelbyville, IL", "cause", "slip and fall")
        );

        when(claimQueryService.queryOpenAndRecentClosedClaims(anyString()))
                .thenReturn(existingClaims);

        when(fuzzyMatchingService.calculateDuplicateScore(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0.92) // High match for claim-001
                .thenReturn(0.30); // Low match for claim-002

        // Act
        decisionOrchestrator.processClaimInitiation(claimId, newClaimPayload);

        // Assert
        verify(claimQueryService).queryOpenAndRecentClosedClaims(claimId);
        verify(fuzzyMatchingService, times(2)).calculateDuplicateScore(anyString(), anyString(), anyString(), anyString());
        verify(taskCreationService).createReviewTask(eq(claimId), eq(0.92), eq("claim-001"));
        
        // Ensure no additional review tasks were created for lower scores
        verify(taskCreationService, times(1)).createReviewTask(anyString(), anyDouble(), anyString());
    }

    // Minimal service contracts for mock orchestration
    private static interface ClaimQueryService {
        List<Map<String, Object>> queryOpenAndRecentClosedClaims(String policyId);
    }

    private static interface FuzzyMatchingService {
        double calculateDuplicateScore(String newAddress, String newCause, String existingAddress, String existingCause);
    }

    private static interface TaskCreationService {
        void createReviewTask(String claimId, double score, String matchedClaimId);
    }

    private static class DecisionOrchestrator {
        private final ClaimQueryService claimQueryService;
        private final FuzzyMatchingService fuzzyMatchingService;
        private final TaskCreationService taskCreationService;
        private final double reviewThreshold;

        DecisionOrchestrator(ClaimQueryService claimQueryService, FuzzyMatchingService fuzzyMatchingService, TaskCreationService taskCreationService, double reviewThreshold) {
            this.claimQueryService = claimQueryService;
            this.fuzzyMatchingService = fuzzyMatchingService;
            this.taskCreationService = taskCreationService;
            this.reviewThreshold = reviewThreshold;
        }

        void processClaimInitiation(String claimId, Map<String, Object> payload) {
            List<Map<String, Object>> existingClaims = claimQueryService.queryOpenAndRecentClosedClaims(claimId);
            String newAddress = (String) payload.get("address");
            String newCause = (String) payload.get("cause");

            for (Map<String, Object> existing : existingClaims) {
                String existingAddress = (String) existing.get("address");
                String existingCause = (String) existing.get("cause");
                double score = fuzzyMatchingService.calculateDuplicateScore(newAddress, newCause, existingAddress, existingCause);
                
                if (score >= reviewThreshold) {
                    taskCreationService.createReviewTask(claimId, score, (String) existing.get("id"));
                    break;
                }
            }
        }
    }
}
