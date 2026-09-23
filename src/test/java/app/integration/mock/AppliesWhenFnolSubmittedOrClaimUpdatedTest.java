package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrchestrationDecisionAppliesConditionTest {

    @Mock
    private EngagementConditionChecker conditionChecker;

    @Mock
    private DecisionExecutionService decisionService;

    @InjectMocks
    private InsuredEngagementOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        reset(conditionChecker, decisionService);
    }

    @Test
    void applies_when_fnol_submitted_or_claim_updated() {
        // Arrange: Mock conditions for FNOL submitted and Claim updated
        when(conditionChecker.isFnolSubmitted(anyString())).thenReturn(true);
        when(conditionChecker.isClaimUpdated(anyString())).thenReturn(true);

        // Arrange: Mock decision execution to return a predictable state
        when(decisionService.executeDecision(any())).thenReturn(DecisionState.TRIGGERED);

        // Act: Verify FNOL submitted scenario
        DecisionState fnolResult = orchestrator.evaluateDecision("fnol-001", EventType.FNOL_SUBMITTED);

        // Assert: FNOL condition applies decision
        assertEquals(DecisionState.TRIGGERED, fnolResult);
        verify(decisionService, times(1)).executeDecision(any());

        // Act: Verify Claim updated scenario
        DecisionState claimResult = orchestrator.evaluateDecision("claim-002", EventType.CLAIM_UPDATED);

        // Assert: Claim updated condition applies decision
        assertEquals(DecisionState.TRIGGERED, claimResult);
        verify(decisionService, times(2)).executeDecision(any());
    }
}
