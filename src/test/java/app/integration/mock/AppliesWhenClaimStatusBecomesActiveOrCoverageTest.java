package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private ClaimStatusValidator claimStatusValidator;

    @InjectMocks
    private RoutingDecisionCalculator routingDecisionCalculator;

    @Test
    void applies_when_claim_status_becomes_active_or_coverage_review() {
        // Scenario 1: Claim status is Active
        Map<String, Object> activePayload = new HashMap<>();
        activePayload.put("claimStatus", "Active");
        when(claimStatusValidator.isValidForRouting(activePayload)).thenReturn(true);

        boolean resultActive = routingDecisionCalculator.calculateDecision("claim-123", activePayload);
        assertTrue(resultActive, "Decision should apply when claim status is Active");

        // Scenario 2: Claim status is Coverage Review
        Map<String, Object> coveragePayload = new HashMap<>();
        coveragePayload.put("claimStatus", "Coverage Review");
        when(claimStatusValidator.isValidForRouting(coveragePayload)).thenReturn(true);

        boolean resultCoverage = routingDecisionCalculator.calculateDecision("claim-456", coveragePayload);
        assertTrue(resultCoverage, "Decision should apply when claim status is Coverage Review");

        // Negative Scenario: Claim status is Draft
        Map<String, Object> draftPayload = new HashMap<>();
        draftPayload.put("claimStatus", "Draft");
        when(claimStatusValidator.isValidForRouting(draftPayload)).thenReturn(false);

        boolean resultDraft = routingDecisionCalculator.calculateDecision("claim-789", draftPayload);
        assertFalse(resultDraft, "Decision should not apply when claim status is Draft");

        verify(claimStatusValidator, times(3)).isValidForRouting(anyMap());
    }
}
