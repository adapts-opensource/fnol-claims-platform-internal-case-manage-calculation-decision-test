package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionMockTest {

    @Mock
    private PolicyReferenceDataCache policyReferenceDataCache;

    private ClaimOrchestrationDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new ClaimOrchestrationDecisionService(policyReferenceDataCache);
    }

    @Test
    void policy_rewritten_with_gap_in_coverage() {
        // Arrange
        String claimId = "CLM-2023-001";
        String policyId = "POL-REWRITTEN-789";
        Map<String, Object> claimInitiationPayload = Map.of(
            "id", claimId,
            "policyId", policyId,
            "event", "CLAIM_INITIATION",
            "policyStatus", "REWITTEN",
            "coverageDetails", Map.of("active", false, "gapPeriod", "2023-10-01T00:00:00Z/2023-10-15T23:59:59Z")
        );

        when(policyReferenceDataCache.getPolicyDetails(policyId))
                .thenReturn(Map.of("status", "ACTIVE_REWRITTEN", "coverageGapDetected", true));

        // Act
        DecisionOutcome outcome = decisionService.evaluateRouting(claimInitiationPayload);

        // Assert
        assertNotNull(outcome);
        assertEquals("ESCALATION_QUEUE", outcome.getRoutingTarget());
        assertTrue(outcome.isFlaggedForManualReview());
        assertEquals("COVERAGE_GAP_DETECTED", outcome.getDecisionCode());
        verify(policyReferenceDataCache, times(1)).getPolicyDetails(policyId);
    }
}
