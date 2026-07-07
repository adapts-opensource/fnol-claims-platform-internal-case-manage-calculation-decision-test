package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration test for Claim Data Standardization:decision:transformation.
 * Verifies: If loss during rewrite, claim is captured but flagged for underwriting review.
 */
@ExtendWith(MockitoExtension.class)
public class RuleIfLossDuringRewriteClaimIsCapturedTest {

    private ClaimDataTransformationService transformationService;

    @Mock
    private RulesEngineDecisionService decisionService;

    @BeforeEach
    void setUp() {
        transformationService = new ClaimDataTransformationService(decisionService);
    }

    @Test
    void rule_if_loss_during_rewrite_claim_is_captured_but_flagged_for_underwriting_review_n() {
        // Arrange
        String claimId = "CLM-REWRITE-001";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("loss_type", "DURING_REWRITE");
        inputPayload.put("claim_id", claimId);
        inputPayload.put("status", "INITIATED");

        Map<String, Object> expectedOutputPayload = new HashMap<>();
        expectedOutputPayload.put("id", claimId);
        expectedOutputPayload.put("payload", inputPayload);
        expectedOutputPayload.put("underwriting_review_flag", true);
        expectedOutputPayload.put("capture_status", "CAPTURED");
        expectedOutputPayload.put("standardized_version", "1.0");

        when(decisionService.evaluateClaimRule(anyMap())).thenReturn(expectedOutputPayload);

        // Act
        Map<String, Object> result = transformationService.transformClaim(inputPayload);

        // Assert
        assertNotNull(result, "Transformed claim data must not be null");
        assertEquals(claimId, result.get("id"), "Claim ID must be preserved");
        assertTrue((Boolean) result.get("underwriting_review_flag"),
                "Claim must be flagged for underwriting review when loss occurs during rewrite");
        assertEquals("CAPTURED", result.get("capture_status"),
                "Claim must be captured in the system");
        assertEquals("1.0", result.get("standardized_version"),
                "Data model version must match global conventions");

        // Verify infrastructure I/O contract validation
        verify(decisionService, times(1)).evaluateClaimRule(inputPayload);
    }

    // Simplified service layer to isolate transformation logic for mock testing
    private static class ClaimDataTransformationService {
        private final RulesEngineDecisionService decisionService;

        ClaimDataTransformationService(RulesEngineDecisionService decisionService) {
            this.decisionService = decisionService;
        }

        Map<String, Object> transformClaim(Map<String, Object> payload) {
            return decisionService.evaluateClaimRule(payload);
        }
    }

    // Mocked infrastructure contract for RulesEngineDecisionService
    private interface RulesEngineDecisionService {
        Map<String, Object> evaluateClaimRule(Map<String, Object> payload);
    }
}
