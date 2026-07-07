package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class StateTransitionMockTest {

    @Mock
    private DecisionEngine decisionEngine;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private StateTransitionService stateTransitionService;

    @Test
    void score_0_85_triggers_review_potential_duplicate_claim_task() {
        // Given
        String claimId = "INC-2024-8842";
        double riskScore = 0.85;
        String expectedTaskName = "Review Potential Duplicate Claim";

        when(decisionEngine.evaluateDuplicateRisk(claimId)).thenReturn(riskScore);

        // When
        stateTransitionService.processDecisionState(claimId);

        // Then
        verify(taskService).createTask(claimId, expectedTaskName);
        verify(decisionEngine).evaluateDuplicateRisk(claimId);
        verifyNoMoreInteractions(decisionEngine, taskService);
    }
}

// Minimal domain contracts for self-contained mock execution
interface DecisionEngine {
    double evaluateDuplicateRisk(String claimId);
}

interface TaskService {
    void createTask(String claimId, String taskName);
}

class StateTransitionService {
    private final DecisionEngine decisionEngine;
    private final TaskService taskService;

    StateTransitionService(DecisionEngine decisionEngine, TaskService taskService) {
        this.decisionEngine = decisionEngine;
        this.taskService = taskService;
    }

    void processDecisionState(String claimId) {
        double score = decisionEngine.evaluateDuplicateRisk(claimId);
        if (score >= 0.85) {
            taskService.createTask(claimId, "Review Potential Duplicate Claim");
        }
    }
}
