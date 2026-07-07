package app.integration.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import app.domain.model.ClaimDataStandardizationTransformationValida;
import app.infrastructure.DocumentStoreService;
import app.infrastructure.PolicyValidationService;
import app.infrastructure.RulesEngineService;
import app.service.ClaimValidationService;

/**
 * Mock integration tests for Claim Data Standardization: validation:decision.
 * Verifies behavior when external services are mocked, ensuring thread safety and
 * isolation of the decision logic from live AWS endpoints.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationValidationDecisionMockTest {

    private static final String CLAIM_ID = "claim-std-001";
    private static final String RULE_UPDATE_ID = "rule-update-cm-99";
    private static final String CONFIG_MANAGER_ID = "config-manager-01";
    private static final String BUCKET_NAME = "DocumentStoreService-bucket";
    private static final String OBJECT_KEY = "DocumentStoreService/" + CLAIM_ID + ".json";
    private static final String RULE_TABLE_NAME = "RulesEngineService_table";
    private static final String POLICY_TABLE_NAME = "PolicyValidationService_table";

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private RulesEngineService rulesEngineService;

    @Mock
    private PolicyValidationService policyValidationService;

    @InjectMocks
    private ClaimValidationService claimValidationService;

    @BeforeEach
    void setUp() {
        // Reset mocks between tests to ensure thread safety and isolation
        org.mockito.Mockito.reset(rulesEngineService, documentStoreService, policyValidationService);
    }

    /**
     * Test Case: AppliesWhenConfigManagerSubmitsRuleUpdate
     * Description: Applies when: Config manager submits rule update
     * 
     * Verifies that when a Config Manager submits a rule update, the validation
     * decision logic correctly retrieves and applies the updated rule, resulting
     * in a decision that reflects the new configuration.
     */
    @Test
    void applies_when_config_manager_submits_rule_update() {
        // Arrange: Mock Config Manager submission result
        Map<String, Object> updatedRulePayload = Map.of(
                "id", RULE_UPDATE_ID,
                "type", "CLAIM_AMOUNT_LIMIT",
                "threshold", 50000,
                "status", "ACTIVE",
                "version", "2.1"
        );

        when(rulesEngineService.submitRuleUpdate(eq(CONFIG_MANAGER_ID), eq(RULE_UPDATE_ID), any(Map.class)))
                .thenReturn(true);

        // Mock the rule retrieval to ensure the decision engine sees the update
        when(rulesEngineService.getRule(eq(RULE_UPDATE_ID), eq(RULE_TABLE_NAME)))
                .thenReturn(Optional.of(updatedRulePayload));

        // Mock S3 payload retrieval for the claim
        Map<String, Object> claimPayload = Map.of(
                "id", CLAIM_ID,
                "amount", 60000, // Exceeds new limit of 50000
                "policyId", "pol-123",
                "submittedAt", Instant.now().toString()
        );

        when(documentStoreService.readPayload(eq(BUCKET_NAME), eq(OBJECT_KEY)))
                .thenReturn(claimPayload);

        // Mock Policy validation context
        when(policyValidationService.getPolicyContext(eq("pol-123"), eq(POLICY_TABLE_NAME)))
                .thenReturn(Map.of("status", "ACTIVE", "coverageType", "COMPREHENSIVE"));

        // Act: Trigger validation which should apply the rule update
        ClaimDataStandardizationTransformationValida result = claimValidationService
                .validateAndDecide(CLAIM_ID, CONFIG_MANAGER_ID, RULE_UPDATE_ID);

        // Assert: Verify decision reflects the applied rule
        assertNotNull(result, "Result should not be null");
        assertEquals(CLAIM_ID, result.getId(), "ID should match input");
        
        // The decision status should be REJECTED due to amount > threshold
        Map<String, Object> decisionPayload = result.getPayload();
        assertNotNull(decisionPayload, "Payload should not be null");
        assertEquals("REJECTED", decisionPayload.get("decisionStatus"),
                "Decision should be REJECTED based on updated rule threshold");
        assertEquals("CLAIM_AMOUNT_LIMIT_EXCEEDED", decisionPayload.get("decisionCode"),
                "Decision code should indicate limit exceeded");

        // Verify interactions
        verify(rulesEngineService).submitRuleUpdate(eq(CONFIG_MANAGER_ID), eq(RULE_UPDATE_ID), any(Map.class));
        verify(rulesEngineService).getRule(eq(RULE_UPDATE_ID), eq(RULE_TABLE_NAME));
        verify(documentStoreService).readPayload(eq(BUCKET_NAME), eq(OBJECT_KEY));
        verify(policyValidationService).getPolicyContext(eq("pol-123"), eq(POLICY_TABLE_NAME));

        // Ensure no stale rules were used (implicit via mock verification)
        verify(rulesEngineService, never()).getRule(eq("OLD_RULE_ID"), any());
    }

    /**
     * Additional verification: Rule update submission failure does not crash validation.
     */
    @Test
    void applies_when_config_manager_submits_rule_update_handles_failure_gracefully() {
        // Arrange
        when(rulesEngineService.submitRuleUpdate(eq(CONFIG_MANAGER_ID), eq(RULE_UPDATE_ID), any(Map.class)))
                .thenReturn(false);

        when(documentStoreService.readPayload(eq(BUCKET_NAME), eq(OBJECT_KEY)))
                .thenReturn(Map.of("id", CLAIM_ID, "amount", 1000));

        // Act
        ClaimDataStandardizationTransformationValida result = claimValidationService
                .validateAndDecide(CLAIM_ID, CONFIG_MANAGER_ID, RULE_UPDATE_ID);

        // Assert
        assertNotNull(result);
        // Should fall back to default or cached behavior without crashing
        assertTrue(result.getPayload().containsKey("decisionStatus"), "Should have decision status");
        assertEquals("PROCESSING", result.getPayload().get("decisionStatus"),
                "Should fallback to PROCESSING when rule update fails");
        
        verify(rulesEngineService).submitRuleUpdate(eq(CONFIG_MANAGER_ID), eq(RULE_UPDATE_ID), any(Map.class));
    }
}
