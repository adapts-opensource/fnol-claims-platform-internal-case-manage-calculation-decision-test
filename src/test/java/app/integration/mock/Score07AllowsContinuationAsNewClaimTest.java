package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class Score07AllowsContinuationAsNewClaimTest {

    @Mock
    private EngagementDecisionOrchestrator decisionOrchestrator;

    @InjectMocks
    private InsuredEngagementOrchestrator insuredEngagementOrchestrator;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization; setup reserved for future preconditions
    }

    @Test
    void score_0_7_allows_continuation_as_new_claim() {
        // Arrange: Simulate a score strictly below the 0.7 threshold
        double engagementScore = 0.65;
        DecisionOutcome expectedOutcome = DecisionOutcome.ALLOW_CONTINUATION_AS_NEW_CLAIM;

        when(decisionOrchestrator.evaluateEngagementScore(engagementScore))
                .thenReturn(expectedOutcome);

        // Act: Trigger the orchestration decision logic
        DecisionOutcome actualOutcome = insuredEngagementOrchestrator.processDecision(engagementScore);

        // Assert: Verify the system allows continuation as a new claim
        assertEquals(expectedOutcome, actualOutcome);
        verify(decisionOrchestrator).evaluateEngagementScore(engagementScore);
    }
}
