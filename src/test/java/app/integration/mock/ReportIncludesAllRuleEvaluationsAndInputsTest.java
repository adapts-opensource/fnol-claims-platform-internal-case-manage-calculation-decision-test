package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Mock test for Multi-Channel FNOL Submission:decision:validation.
 * Verifies that the report includes all rule evaluations and inputs.
 */
@ExtendWith(MockitoExtension.class)
class ReportIncludesAllRuleEvaluationsAndInputsTest {

    @Mock
    private RuleEvaluationService ruleEvaluationService;

    @Mock
    private InputAggregationService inputAggregationService;

    @Mock
    private PolicyValidatorService policyValidatorService;

    @InjectMocks
    private FnolDecisionValidationService validationService;

    private static final String TEST_ENTITY_ID = "fnol-entity-789";
    private static final String CHANNEL_WEB = "WEB_PORTAL";
    private static final String RULE_AUTO_COVERAGE = "AUTO_COVERAGE_VALIDATION";
    private static final String RULE_FRAUD_CHECK = "FRAUD_DETECTION_V2";
    private static final String RULE_DATA_COMPLETENESS = "DATA_COMPLETENESS_CHECK";

    @BeforeEach
    void setUp() {
        // Mocks are initialized by MockitoExtension
        // Reset behavior if running multiple tests in same JVM instance
        reset(ruleEvaluationService, inputAggregationService, policyValidatorService);
    }

    @Test
    void report_includes_all_rule_evaluations_and_inputs() {
        // Arrange: Prepare payload
        Map<String, Object> submissionPayload = new HashMap<>();
        submissionPayload.put("id", TEST_ENTITY_ID);
        submissionPayload.put("channel", CHANNEL_WEB);
        submissionPayload.put("claimType", "AUTO");
        submissionPayload.put("lossDate", "2023-11-15");
        submissionPayload.put("insuredName", "Jane Doe");

        // Arrange: Mock Input Aggregation
        Map<String, Object> aggregatedInputs = new HashMap<>();
        aggregatedInputs.put("insuredName", "Jane Doe");
        aggregatedInputs.put("lossDate", "2023-11-15");
        aggregatedInputs.put("policyNumber", "POL-998877");
        aggregatedInputs.put("deductible", 500.0);
        
        when(inputAggregationService.aggregateInputs(any())).thenReturn(aggregatedInputs);

        // Arrange: Mock Policy Validation
        Map<String, Object> policyData = new HashMap<>();
        policyData.put("status", "ACTIVE");
        policyData.put("coverageType", "COMPREHENSIVE");
        when(policyValidatorService.validatePolicy(any())).thenReturn(policyData);

        // Arrange: Mock Rule Evaluations
        Map<String, Object> ruleEvaluations = new HashMap<>();
        ruleEvaluations.put(RULE_AUTO_COVERAGE, Map.of("result", "PASS", "score", 100, "details", "Coverage matches claim type"));
        ruleEvaluations.put(RULE_FRAUD_CHECK, Map.of("result", "REVIEW", "score", 75, "details", "Minor anomaly detected"));
        ruleEvaluations.put(RULE_DATA_COMPLETENESS, Map.of("result", "PASS", "score", 100, "details", "All required fields present"));
        
        when(ruleEvaluationService.evaluateRules(any())).thenReturn(ruleEvaluations);

        // Act: Invoke service
        var validationResult = validationService.processValidation(TEST_ENTITY_ID, submissionPayload);

        // Assert: Verify result structure
        assertNotNull(validationResult);
        assertNotNull(validationResult.getPayload());

        // Assert: Verify Inputs are included in report
        assertTrue(validationResult.getPayload().containsKey("inputs"), "Report must contain inputs");
        @SuppressWarnings("unchecked")
        Map<String, Object> reportInputs = (Map<String, Object>) validationResult.getPayload().get("inputs");
        assertEquals(aggregatedInputs, reportInputs, "Report inputs must match aggregated inputs");
        assertEquals("Jane Doe", reportInputs.get("insuredName"));
        assertEquals(500.0, reportInputs.get("deductible"));

        // Assert: Verify Rule Evaluations are included in report
        assertTrue(validationResult.getPayload().containsKey("ruleEvaluations"), "Report must contain rule evaluations");
        @SuppressWarnings("unchecked")
        Map<String, Object> reportEvaluations = (Map<String, Object>) validationResult.getPayload().get("ruleEvaluations");
        assertEquals(ruleEvaluations, reportEvaluations, "Report evaluations must match rule engine output");

        // Assert: Verify all expected rules are present
        assertTrue(reportEvaluations.containsKey(RULE_AUTO_COVERAGE), "Missing rule evaluation: " + RULE_AUTO_COVERAGE);
        assertTrue(reportEvaluations.containsKey(RULE_FRAUD_CHECK), "Missing rule evaluation: " + RULE_FRAUD_CHECK);
        assertTrue(reportEvaluations.containsKey(RULE_DATA_COMPLETENESS), "Missing rule evaluation: " + RULE_DATA_COMPLETENESS);

        // Assert: Verify rule details integrity
        @SuppressWarnings("unchecked")
        Map<String, Object> autoRule = (Map<String, Object>) reportEvaluations.get(RULE_AUTO_COVERAGE);
        assertEquals("PASS", autoRule.get("result"));
        assertEquals("Coverage matches claim type", autoRule.get("details"));

        // Assert: Verify mock interactions
        verify(inputAggregationService).aggregateInputs(any());
        verify(policyValidatorService).validatePolicy(any());
        verify(ruleEvaluationService).evaluateRules(aggregatedInputs);
    }
}
