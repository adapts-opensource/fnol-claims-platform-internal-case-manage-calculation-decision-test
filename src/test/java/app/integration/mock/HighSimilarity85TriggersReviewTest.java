package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JUnit 5 mock test for Claim Initiation & Routing: orchestration: transformation.
 * Verifies that a high similarity score (>=85%) correctly triggers a review routing status.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimOrchestrationTransformationTest {

    @Mock
    private SimilarityService similarityService;

    @Mock
    private RoutingService routingService;

    private ClaimTransformationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimTransformationOrchestrator(similarityService, routingService);
    }

    @Test
    void high_similarity_85_triggers_review() {
        // Arrange
        Map<String, Object> claimInput = Map.of(
            "claimId", "CLM-85-TEST",
            "description", "Rear-end collision on highway",
            "policyId", "POL-111"
        );
        List<Map<String, Object>> existingClaims = List.of();

        when(similarityService.calculateSimilarity(anyMap(), anyList())).thenReturn(85.0);

        // Act
        Map<String, Object> transformedClaim = orchestrator.transformAndRoute(claimInput, existingClaims);

        // Assert
        verify(routingService).routeClaim(anyMap(), "REVIEW");
        assertTrue((Boolean) transformedClaim.get("requiresReview"));
        assertEquals("REVIEW", transformedClaim.get("routingStatus"));
    }

    // Minimal dependency interfaces to keep test self-contained and mockable
    interface SimilarityService {
        double calculateSimilarity(Map<String, Object> claim, List<Map<String, Object>> existingClaims);
    }

    interface RoutingService {
        void routeClaim(Map<String, Object> claim, String status);
    }

    // Service under test
    static class ClaimTransformationOrchestrator {
        private final SimilarityService similarityService;
        private final RoutingService routingService;

        ClaimTransformationOrchestrator(SimilarityService similarityService, RoutingService routingService) {
            this.similarityService = similarityService;
            this.routingService = routingService;
        }

        Map<String, Object> transformAndRoute(Map<String, Object> claim, List<Map<String, Object>> existingClaims) {
            double similarity = similarityService.calculateSimilarity(claim, existingClaims);
            boolean needsReview = similarity >= 85.0;

            Map<String, Object> transformed = new java.util.HashMap<>(claim);
            transformed.put("requiresReview", needsReview);
            transformed.put("routingStatus", needsReview ? "REVIEW" : "AUTO_APPROVE");

            if (needsReview) {
                routingService.routeClaim(transformed, "REVIEW");
            }
            return transformed;
        }
    }
}
