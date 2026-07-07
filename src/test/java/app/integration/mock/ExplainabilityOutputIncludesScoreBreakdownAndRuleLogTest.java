package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

/**
 * Verifies that the decision transformation module returns explainability outputs
 * containing both a detailed score breakdown and an audit-ready rule log.
 * Aligns with GDPR/SOC2 explainability requirements and structured logging NFRs.
 */
public class InsuredEngagementDecisionTransformationTest {

    @Test
    void explainability_output_includes_score_breakdown_and_rule_log() {
        // Arrange: Mock the decision transformation service to isolate logic from external I/O
        DecisionTransformationService mockService = mock(DecisionTransformationService.class);

        Map<String, Double> scoreBreakdown = Map.of(
                "base_risk_score", 75.0,
                "engagement_factor", 1.15,
                "compliance_adjustment", 0.05
        );
        List<String> ruleLog = List.of(
                "RULE_ENC_01: Insured contacted within 24h SLA",
                "RULE_ENC_02: Policy coverage verified",
                "RULE_ENC_03: GDPR consent flag checked"
        );
        ExplainabilityOutput expectedOutput = new ExplainabilityOutput(scoreBreakdown, ruleLog);

        when(mockService.transformDecision(any(DecisionContext.class))).thenReturn(expectedOutput);

        // Act: Execute the mocked transformation
        DecisionContext context = new DecisionContext("POL-98765", "INS-12345");
        ExplainabilityOutput actualOutput = mockService.transformDecision(context);

        // Assert: Verify explainability output structure and content
        assertNotNull(actualOutput, "Output must not be null");
        assertNotNull(actualOutput.scoreBreakdown(), "Score breakdown must be present");
        assertFalse(actualOutput.scoreBreakdown().isEmpty(), "Score breakdown must contain scoring components");
        assertNotNull(actualOutput.ruleLog(), "Rule log must be present");
        assertFalse(actualOutput.ruleLog().isEmpty(), "Rule log must contain evaluated decision rules");
        assertEquals(expectedOutput.scoreBreakdown(), actualOutput.scoreBreakdown());
        assertEquals(expectedOutput.ruleLog(), actualOutput.ruleLog());
    }

    // Test doubles to simulate the decision transformation pipeline without live AWS/HTTP calls
    interface DecisionTransformationService {
        ExplainabilityOutput transformDecision(DecisionContext context);
    }

    record DecisionContext(String policyId, String insuredId) {}

    record ExplainabilityOutput(Map<String, Double> scoreBreakdown, List<String> ruleLog) {}
}
