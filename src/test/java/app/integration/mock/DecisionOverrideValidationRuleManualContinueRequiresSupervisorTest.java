package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 mock test for Insured Engagement & Tracking:decision:transformation.
 * Verifies that manual continue actions under Override Validation require supervisor routing and audit logging.
 */
@ExtendWith(MockitoExtension.class)
public class DecisionOverrideValidationRuleManualContinueRequiresSupervisorTest {

    @Mock
    private SupervisorTaskRouter supervisorTaskRouter;

    @Mock
    private AuditLogger auditLogger;

    private DecisionTransformationService service;

    @BeforeEach
    void setUp() {
        service = new DecisionTransformationService(supervisorTaskRouter, auditLogger);
    }

    @Test
    void decision_override_validation_rule_manual_continue_requires_supervisor_approval_expected_outcome_task_routed_to_supervisor_audit_logged() {
        // Arrange
        String claimId = "CLM-INS-2024-001";
        String decision = "Override Validation";
        String action = "Manual Continue";

        // Act
        service.processDecision(claimId, decision, action);

        // Assert
        verify(supervisorTaskRouter, times(1))
                .routeToSupervisor(eq(claimId), eq("Manual continue requires supervisor approval"));
        verify(auditLogger, times(1))
                .log(eq(claimId), eq("DECISION_TRANSFORM"), eq("Task routed to supervisor for manual continue override validation"));
        verifyNoMoreInteractions(supervisorTaskRouter, auditLogger);
    }

    // Minimal domain interfaces simulating external I/O contracts
    interface SupervisorTaskRouter {
        void routeToSupervisor(String claimId, String reason);
    }

    interface AuditLogger {
        void log(String claimId, String eventType, String details);
    }

    // Service containing the business rule logic for decision transformation
    static class DecisionTransformationService {
        private final SupervisorTaskRouter router;
        private final AuditLogger logger;

        DecisionTransformationService(SupervisorTaskRouter router, AuditLogger logger) {
            this.router = router;
            this.logger = logger;
        }

        void processDecision(String claimId, String decision, String action) {
            if ("Override Validation".equals(decision) && "Manual Continue".equals(action)) {
                router.routeToSupervisor(claimId, "Manual continue requires supervisor approval");
                logger.log(claimId, "DECISION_TRANSFORM", "Task routed to supervisor for manual continue override validation");
            }
        }
    }
}
