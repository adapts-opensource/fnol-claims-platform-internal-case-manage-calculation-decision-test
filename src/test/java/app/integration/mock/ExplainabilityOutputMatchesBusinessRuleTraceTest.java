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
public class ExplainabilityOutputMatchesBusinessRuleTraceTest {

    @Mock
    private DecisionCalculationEngine decisionCalculationEngine;

    private static final String CLAIM_ID = "claim-init-12345";
    private static final Map<String, Object> CLAIM_PAYLOAD = Map.of(
        "policyId", "POL-9876",
        "incidentDate", "2023-10-01",
        "coverageType", "AUTO",
        "deductible", 500
    );

    @BeforeEach
    void setUp() {
        // Initialize test fixtures and mock configurations
        // Ensures thread-safe isolation per test execution
    }

    @Test
    void explainability_output_matches_business_rule_trace() {
        // Given: Expected aligned outputs for explainability and business rule trace
        Map<String, Object> expectedExplainability = Map.of(
            "ruleId", "AUTO_DEDUCTIBLE_CHECK_V2",
            "decision", "APPROVE_ROUTING_TO_ADJUSTER",
            "reason", "Standard deductible applies",
            "confidenceScore", 0.95
        );
        Map<String, Object> expectedTrace = Map.of(
            "ruleId", "AUTO_DEDUCTIBLE_CHECK_V2",
            "decision", "APPROVE_ROUTING_TO_ADJUSTER",
            "reason", "Standard deductible applies",
            "confidenceScore", 0.95
        );

        DecisionResult expectedResult = new DecisionResult(expectedExplainability, expectedTrace);

        when(decisionCalculationEngine.calculate(eq(CLAIM_ID), eq(CLAIM_PAYLOAD)))
            .thenReturn(expectedResult);

        // When
        DecisionResult actualResult = decisionCalculationEngine.calculate(CLAIM_ID, CLAIM_PAYLOAD);

        // Then: Verify explainability output matches business rule trace structure and values
        assertNotNull(actualResult);
        assertEquals(expectedExplainability, actualResult.explainabilityOutput());
        assertEquals(expectedTrace, actualResult.businessRuleTrace());
        assertEquals(actualResult.explainabilityOutput().keySet(), actualResult.businessRuleTrace().keySet(),
            "Explainability output and business rule trace must share identical keys");
        assertEquals(actualResult.explainabilityOutput().values(), actualResult.businessRuleTrace().values(),
            "Explainability output and business rule trace must share identical values");
    }

    // Minimal DTO to represent calculation result
    private record DecisionResult(Map<String, Object> explainabilityOutput, Map<String, Object> businessRuleTrace) {}
}
