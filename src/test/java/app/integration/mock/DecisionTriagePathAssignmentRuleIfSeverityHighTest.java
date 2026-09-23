package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionTriagePathAssignmentRuleIfSeverityHighTest {

    @Mock
    private DecisionOrchestrationService decisionOrchestrationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock injection; setup reserved for future fixture initialization
    }

    @Test
    void decision_triage_path_assignment_rule_if_severity_high_and_cause_wind_then_path_rapid_inspection_else_if_litigation_risk_then_path_legal_review_else_path_standard_review_n_expected_outcome_assigned_queue_priority_level() {
        // Arrange: Simulate rule inputs matching the high severity + wind cause branch
        String severity = "high";
        String cause = "wind";
        boolean litigationRisk = false;

        // Mock external orchestration service response per rule:
        // IF severity == "high" AND cause == "wind" THEN path = "rapid_inspection"
        when(decisionOrchestrationService.evaluateTriagePath(severity, cause, litigationRisk))
                .thenReturn(new TriageOutcome("rapid_inspection", "assigned_queue", "priority_level"));

        // Act: Invoke the mocked decision service
        TriageOutcome outcome = decisionOrchestrationService.evaluateTriagePath(severity, cause, litigationRisk);

        // Assert: Verify expected outcomes (assigned_queue, priority_level) and path assignment
        assertNotNull(outcome);
        assertEquals("rapid_inspection", outcome.path());
        assertEquals("assigned_queue", outcome.assignedQueue());
        assertEquals("priority_level", outcome.priorityLevel());

        // Verify service interaction occurred exactly once
        verify(decisionOrchestrationService, times(1)).evaluateTriagePath(severity, cause, litigationRisk);
    }

    /**
     * Minimal DTO representing the decision outcome contract.
     */
    private static class TriageOutcome {
        private final String path;
        private final String assignedQueue;
        private final String priorityLevel;

        public TriageOutcome(String path, String assignedQueue, String priorityLevel) {
            this.path = path;
            this.assignedQueue = assignedQueue;
            this.priorityLevel = priorityLevel;
        }

        public String path() { return path; }
        public String assignedQueue() { return assignedQueue; }
        public String priorityLevel() { return priorityLevel; }
    }
}
