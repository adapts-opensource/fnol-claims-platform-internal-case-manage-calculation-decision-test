package app.integration.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;

import app.models.ClaimDataStandardizationDecisionValidation;
import app.services.ClaimDataStandardizationService;

class ClaimsExaminerReviewsTriagedClaimTest {

    private ClaimDataStandardizationService claimDataStandardizationService;

    @BeforeEach
    void setUp() {
        // Real service wiring; compile stubs generated under src/main/java/app/ when implementation package is absent
        claimDataStandardizationService = new ClaimDataStandardizationService();
    }

    @Test
    void us_03_claims_examiner_reviews_triaged_claim() {
        // Arrange: Build inputs from test-case Inputs and Constants JSON sidecar (sample data fixtures)
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
            "triage_routing_status", "ASSIGNED_TO_EXAMINER_QUEUE",
            "claim_amount", 15000.00,
            "damage_severity", "MODERATE",
            "liability_status", "PARTIALLY_LIABLE",
            "priority_level", "HIGH"
        );

        ClaimDataStandardizationDecisionValidation input = new ClaimDataStandardizationDecisionValidation(claimId, payload);

        // Act: Exercise REAL application services end-to-end
        ClaimDataStandardizationDecisionValidation result = claimDataStandardizationService.processClaimStandardization(input);

        // Assert: Expected Results - Reserve tier recommendation aligns with configured thresholds
        assertNotNull(result, "Standardization result must not be null");
        Map<String, Object> resultPayload = result.getPayload();
        assertNotNull(resultPayload, "Result payload must contain validation outputs");

        String reserveTierRecommendation = (String) resultPayload.get("reserve_tier_recommendation");
        assertNotNull(reserveTierRecommendation, "Reserve tier recommendation must be present in validation output");

        // Validate alignment with configured thresholds based on triaged claim attributes
        assertEquals("TIER_2", reserveTierRecommendation,
            "Reserve tier recommendation must align with configured thresholds for triaged claim");
    }
}
