package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 test class for Claim Data Standardization:orchestration:decision.
 * Verifies FNOL with date of loss outside policy period routes to coverage review.
 */
@ExtendWith(MockitoExtension.class)
public class FnolCoverageReviewDateDecisionTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private StateTransitionService stateTransitionService;

    private DecisionOrchestrator decisionOrchestrator;

    @BeforeEach
    void setUp() {
        decisionOrchestrator = new DecisionOrchestrator(policyValidationService, stateTransitionService);
    }

    @Test
    void fnolCoverageReviewDateOfLossDecision() {
        // Arrange
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("channel", "Portal");
        inputPayload.put("policy_number", "POL-33333");
        inputPayload.put("date_of_loss", "2024-04-01");
        inputPayload.put("policy_effective_date", "2024-05-01");
        inputPayload.put("cause_of_loss", "Wind");
        inputPayload.put("tenant_code", "FL01");

        when(policyValidationService.matchPolicy("POL-33333")).thenReturn("Match");
        when(policyValidationService.validateDate("2024-04-01", "2024-05-01")).thenReturn("Outside Period");

        // Act
        Map<String, Object> resultPayload = decisionOrchestrator.decide(inputPayload);

        // Assert
        assertEquals("Match", resultPayload.get("policy_match_status"));
        assertEquals("Outside Period", resultPayload.get("date_validation"));
        assertEquals("Coverage Triage", resultPayload.get("state"));
        assertEquals(Arrays.asList("Review FNOL", "Acknowledge Claim", "Review Coverage"), resultPayload.get("tasks"));

        // Verify infrastructure interactions
        verify(stateTransitionService).transitionTo(eq("Coverage Triage"), anyMap());
    }
}
