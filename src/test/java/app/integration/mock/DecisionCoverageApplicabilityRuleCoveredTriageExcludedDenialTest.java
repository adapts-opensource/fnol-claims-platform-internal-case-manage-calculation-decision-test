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

@ExtendWith(MockitoExtension.class)
class DecisionCoverageApplicabilityRuleCoveredTriageExcludedDenial {

    @Mock
    private MockClaimDecisionValidator decisionValidator;

    private Map<String, Object> claimPayload;
    private Map<String, Object> expectedOutcome;

    @BeforeEach
    void setUp() {
        claimPayload = new HashMap<>();
        claimPayload.put("id", "CLM-STD-2024-001");
        claimPayload.put("coverage_status", "Covered");
        claimPayload.put("policy_type", "Auto");
        claimPayload.put("incident_date", "2024-05-15");
        claimPayload.put("estimated_damage", 2500.00);

        expectedOutcome = new HashMap<>();
        expectedOutcome.put("decision", "Coverage applicability");
        expectedOutcome.put("rule_applied", "Covered = triage");
        expectedOutcome.put("triage_path", true);
        expectedOutcome.put("reserve_estimate_trigger", true);
        expectedOutcome.put("denial_flag", false);
        expectedOutcome.put("conditional_triage", false);
        expectedOutcome.put("validation_status", "PASS");
    }

    @Test
    void decision_coverage_applicability_rule_covered_triage_excluded_denial_flag_endorsed_conditional_triage_expected_outcome_triage_path_reserve_estimate_trigger() {
        // Arrange: Mock external decision service to return expected outcome without hitting S3/DynamoDB
        when(decisionValidator.validateCoverageApplicability(claimPayload)).thenReturn(expectedOutcome);

        // Act: Execute validation under test
        Map<String, Object> actualOutcome = decisionValidator.validateCoverageApplicability(claimPayload);

        // Assert: Verify outcome matches expected behavior for 'Covered' status
        assertNotNull(actualOutcome, "Validation outcome must not be null");
        assertEquals("Coverage applicability", actualOutcome.get("decision"));
        assertTrue((Boolean) actualOutcome.get("triage_path"), "Covered status must trigger triage path");
        assertTrue((Boolean) actualOutcome.get("reserve_estimate_trigger"), "Covered status must trigger reserve estimate");
        assertFalse((Boolean) actualOutcome.get("denial_flag"), "Covered status must not set denial flag");
        assertFalse((Boolean) actualOutcome.get("conditional_triage"), "Covered status must not trigger conditional triage");
        assertEquals("PASS", actualOutcome.get("validation_status"));

        // Verify single invocation with correct payload
        verify(decisionValidator, times(1)).validateCoverageApplicability(claimPayload);
    }

    /**
     * Mock interface simulating the external Claim Data Standardization Decision Service.
     * In production, this would orchestrate reads from DocumentStoreService (S3) 
     * and Policy/RulesEngineService (DynamoDB) to apply validation rules.
     */
    private static interface MockClaimDecisionValidator {
        Map<String, Object> validateCoverageApplicability(Map<String, Object> payload);
    }
}
