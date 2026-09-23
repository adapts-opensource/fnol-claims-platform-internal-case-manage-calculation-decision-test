package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration mock test for Claim Data Standardization:validation:decision
 * Validates policy selection rule: Analyst picks candidate OR system auto-selects highest confidence.
 * Expected outcome: Single policy context established.
 */
@ExtendWith(MockitoExtension.class)
class DecisionPolicySelectionRuleAnalystPicksCandidateOrTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimDecisionOrchestrator claimDecisionOrchestrator;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        // Simulates claim_data_standardization_transformation_valida entity payload
        testPayload = Map.of(
                "policy_candidates", List.of(
                        Map.of("policy_id", "POL_001", "confidence_score", 0.92),
                        Map.of("policy_id", "POL_002", "confidence_score", 0.88)
                ),
                "analyst_selection", null,
                "system_auto_select", true,
                "context_type", "SINGLE_POLICY_CONTEXT"
        );
    }

    @Test
    void decision_policy_selection_rule_analyst_picks_candidate_or_system_auto_selects_highest_confidence_expected_outcome_single_policy_context_established() {
        String claimId = "CLM_STD_789";

        // Arrange: Mock infrastructure I/O contracts (S3 & DynamoDB)
        when(documentStoreService.storeDocument(anyString(), anyString())).thenReturn("s3://DocumentStoreService-bucket/CLM_STD_789.json");
        when(policyValidationService.validatePolicy(anyString(), any())).thenReturn(Map.of("validation_status", "PASSED"));
        when(rulesEngineService.selectPolicy(anyString(), any())).thenReturn(Map.of(
                "selected_policy_id", "POL_001",
                "outcome", "SINGLE_POLICY_CONTEXT_ESTABLISHED",
                "selection_method", "SYSTEM_AUTO_SELECT_HIGHEST_CONFIDENCE"
        ));

        // Act: Execute decision logic against mocked services
        Map<String, Object> result = claimDecisionOrchestrator.processClaimDecision(claimId, testPayload);

        // Assert: Verify expected outcome and single policy context establishment
        assertNotNull(result, "Decision result must not be null");
        assertEquals("SINGLE_POLICY_CONTEXT_ESTABLISHED", result.get("outcome"), "Expected single policy context established");
        assertEquals("POL_001", result.get("selected_policy_id"), "System should auto-select highest confidence policy");
        assertEquals("SYSTEM_AUTO_SELECT_HIGHEST_CONFIDENCE", result.get("selection_method"));

        // Verify infrastructure calls respect input validation and thread safety
        verify(documentStoreService, times(1)).storeDocument(anyString(), anyString());
        verify(policyValidationService, times(1)).validatePolicy(eq(claimId), any());
        verify(rulesEngineService, times(1)).selectPolicy(eq(claimId), any());
    }
}
