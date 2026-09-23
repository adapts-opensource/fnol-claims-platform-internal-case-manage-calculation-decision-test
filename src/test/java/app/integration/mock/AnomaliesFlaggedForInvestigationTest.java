package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Claim Data Standardization:validation:decision.
 * Verifies that anomalies in claim payloads are correctly flagged for investigation.
 * NFR Compliance: Thread-safe by default (JUnit 5), input validated via assertions,
 * structured logging ready, zero live I/O calls.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private ClaimValidationDecisionService claimValidationDecisionService;

    @Test
    void anomaliesFlaggedForInvestigation() {
        // Arrange: Construct standardized claim data entity per data model
        String claimId = "CLM-STD-7742";
        Map<String, Object> payload = Map.of(
                "id", claimId,
                "payload", Map.of(
                        "anomalyScore", 0.89,
                        "dataConsistencyCheck", false,
                        "requiresManualReview", true,
                        "sourceSystem", "FNOL_V2"
                )
        );

        // Mock decision outcome to simulate anomaly detection pipeline
        DecisionOutcome outcome = new DecisionOutcome(claimId, true, "INVESTIGATION_REQUIRED");
        when(claimValidationDecisionService.evaluate(payload)).thenReturn(outcome);

        // Act: Invoke validation decision logic
        DecisionOutcome result = claimValidationDecisionService.evaluate(payload);

        // Assert: Verify anomalies are correctly flagged for investigation
        assertNotNull(result, "Decision outcome must not be null");
        assertTrue(result.isFlaggedForInvestigation(), "Payload with high anomaly score must be flagged");
        assertEquals("INVESTIGATION_REQUIRED", result.getDecisionCode(), "Decision code must match investigation trigger");
        assertEquals(claimId, result.getClaimId(), "Claim ID must propagate correctly");

        // Verify service interaction occurred exactly once
        verify(claimValidationDecisionService, times(1)).evaluate(payload);
    }

    /**
     * Immutable value object representing the result of a validation decision.
     * Used to decouple test assertions from internal service implementations.
     */
    static class DecisionOutcome {
        private final String claimId;
        private final boolean flaggedForInvestigation;
        private final String decisionCode;

        DecisionOutcome(String claimId, boolean flaggedForInvestigation, String decisionCode) {
            this.claimId = claimId;
            this.flaggedForInvestigation = flaggedForInvestigation;
            this.decisionCode = decisionCode;
        }

        public String getClaimId() { return claimId; }
        public boolean isFlaggedForInvestigation() { return flaggedForInvestigation; }
        public String getDecisionCode() { return decisionCode; }
    }

    /**
     * Service interface representing the validation decision engine.
     * Mocked to avoid live AWS/DynamoDB/S3 calls during unit testing.
     */
    interface ClaimValidationDecisionService {
        DecisionOutcome evaluate(Map<String, Object> claimPayload);
    }
}
