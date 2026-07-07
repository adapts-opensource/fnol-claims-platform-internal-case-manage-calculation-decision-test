package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock tests for Claim Initiation & Routing:decision:calculation.
 * Validates decision logic with mocked infrastructure and dependencies.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationMockTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private ReasonCodeValidator reasonCodeValidator;

    @Mock
    private ClaimContextService claimContextService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private Logger structuredLogger;

    @InjectMocks
    private DecisionCalculationService decisionCalculationService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> contextUpdateCaptor;

    private static final String CLAIM_ID = "claim-init-001";
    private static final String POLICY_ID = "policy-active-001";
    private static final String VALID_REASON_CODE = "REASON_001";
    private static final String CACHE_KEY_PREFIX = "Cache & Reference Data:cache:POLICY:";

    @BeforeEach
    void setUp() {
        // Ensure fresh mocks for thread safety and isolation per test execution
        Mockito.framework().clearInlineMocks();
    }

    /**
     * Test Case: DecisionOverrideValidityRulePolicyActiveRecentValid
     * Scenario: Policy is active/recent, reason code is valid.
     * Expected: Decision is Override Validity, Claim context is updated or rejected.
     */
    @Test
    void decisionOverrideValidityRulePolicyActiveRecentValidReasonCodeExpectedOutcomeClaimContextUpdatedOrRejected() {
        // Arrange
        Map<String, Object> payload = Map.of(
            "id", CLAIM_ID,
            "payload", Map.of(
                "policyId", POLICY_ID,
                "reasonCode", VALID_REASON_CODE,
                "requestedDecision", "OVERRIDE_VALIDITY"
            )
        );

        // Mock Policy Active/Recent check
        when(policyValidationService.isPolicyActiveOrRecent(POLICY_ID)).thenReturn(true);

        // Mock Valid Reason Code check
        when(reasonCodeValidator.isValid(VALID_REASON_CODE)).thenReturn(true);

        // Mock Redis Cache miss (service handles fetch)
        String cacheKey = CACHE_KEY_PREFIX + POLICY_ID;
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);

        // Mock structured logging
        doNothing().when(structuredLogger).info(anyString(), any());

        // Act
        DecisionResult result = decisionCalculationService.calculateDecision(payload);

        // Assert
        assertNotNull(result, "Decision result must not be null");
        assertEquals(DecisionOutcome.OVERRIDE_VALIDITY, result.getDecision(),
            "Decision should be Override Validity based on active policy and valid reason");

        // Verify Claim Context interaction (Updated or Rejected)
        verify(claimContextService, atLeastOnce()).processClaimContext(eq(CLAIM_ID), contextUpdateCaptor.capture());

        Map<String, Object> capturedContext = contextUpdateCaptor.getValue();
        assertNotNull(capturedContext, "Claim context update payload must not be null");
        assertEquals(DecisionOutcome.OVERRIDE_VALIDITY.name(), capturedContext.get("decisionOutcome"),
            "Context must reflect the override decision");

        // Verify infrastructure interactions
        verify(redisTemplate).opsForValue().get(cacheKey);
        verify(policyValidationService).isPolicyActiveOrRecent(POLICY_ID);
        verify(reasonCodeValidator).isValid(VALID_REASON_CODE);

        // Verify security and observability contracts
        verify(structuredLogger).info(eq("Decision calculation completed for claim"), any());
        // Ensure no exceptions during validation (input validation mock success)
        verify(reasonCodeValidator).isValid(VALID_REASON_CODE);
    }
}
