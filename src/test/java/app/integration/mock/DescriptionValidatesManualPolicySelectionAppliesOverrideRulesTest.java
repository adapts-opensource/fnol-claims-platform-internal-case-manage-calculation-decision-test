package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ClaimDecisionCalculationManualPolicySelectionTest {

    private static final Logger LOG = LoggerFactory.getLogger(ClaimDecisionCalculationManualPolicySelectionTest.class);

    @Mock
    private PolicySelectionValidator validator;

    @Mock
    private OverrideRuleEngine overrideEngine;

    @Mock
    private ClaimContextService contextService;

    @Mock
    private JustificationLogger justificationLogger;

    @Mock
    private RedisClient redisClient;

    private ClaimDecisionCalculator calculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        calculator = new ClaimDecisionCalculator(validator, overrideEngine, contextService, justificationLogger, redisClient);
    }

    @Test
    void description_validates_manual_policy_selection_applies_override_rules_updates_claim_context_and_logs_justification() {
        // Arrange
        String claimId = "CLM-TEST-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("selectionMode", "MANUAL");
        payload.put("policyId", "POL-MANUAL-001");
        payload.put("overrideRules", List.of("OVERRIDE_RULE_1", "OVERRIDE_RULE_2"));

        String cacheKey = "Cache & Reference Data:cache:POL-MANUAL-001";
        when(redisClient.get(cacheKey)).thenReturn("ACTIVE");
        when(validator.isValid("POL-MANUAL-001")).thenReturn(true);

        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> justificationCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        calculator.processDecisionCalculation(claimId, payload);

        // Assert
        verify(validator).isValid("POL-MANUAL-001");
        verify(overrideEngine).applyOverrides(payload);
        verify(contextService).updateContext(eq(claimId), contextCaptor.capture());
        verify(justificationLogger).logJustification(eq(claimId), justificationCaptor.capture());
        verify(redisClient).get(cacheKey);

        assertFalse(contextCaptor.getValue().isEmpty(), "Claim context should be updated after manual selection");
        assertTrue(justificationCaptor.getValue().contains("Manual policy selection"), "Justification log should contain rationale");
        LOG.info("Test passed: Manual policy selection validated, overrides applied, context updated, and justification logged.");
    }
}
