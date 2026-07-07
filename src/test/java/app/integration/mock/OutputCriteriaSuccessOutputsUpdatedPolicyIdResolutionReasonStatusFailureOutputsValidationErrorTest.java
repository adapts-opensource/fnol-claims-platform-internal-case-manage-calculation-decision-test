package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateTransitionDecisionMockTest {

    @Mock
    private DecisionStateTransitionService mockService;

    private DecisionStateTransitionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DecisionStateTransitionHandler(mockService);
    }

    @Test
    void output_criteria_success_outputs_updated_policy_id_resolution_reason_status_failure_outputs_validation_error_insufficient_permissions() {
        // Arrange: Success scenario
        String policyId = "POL-98765";
        String reason = "Underwriter approved claim";
        String status = "APPROVED";

        Map<String, Object> successPayload = Map.of(
                "updated_policy_id", policyId,
                "resolution_reason", reason,
                "status", status
        );

        when(mockService.transitionState("CASE-001", "DECISION_REVIEW")).thenReturn(successPayload);

        // Act: Success
        Map<String, Object> resultSuccess = handler.executeTransition("CASE-001", "DECISION_REVIEW");

        // Assert: Success outputs
        assertNotNull(resultSuccess);
        assertEquals(policyId, resultSuccess.get("updated_policy_id"));
        assertEquals(reason, resultSuccess.get("resolution_reason"));
        assertEquals(status, resultSuccess.get("status"));

        // Arrange: Failure scenario (Validation Error)
        Map<String, Object> validationErrorPayload = Map.of(
                "validation_error", "Invalid transition from current state"
        );
        when(mockService.transitionState("CASE-002", "INVALID_STEP")).thenReturn(validationErrorPayload);

        // Act & Assert: Failure outputs (validation_error)
        Map<String, Object> resultValidation = handler.executeTransition("CASE-002", "INVALID_STEP");
        assertNotNull(resultValidation);
        assertEquals("Invalid transition from current state", resultValidation.get("validation_error"));

        // Arrange: Failure scenario (Insufficient Permissions)
        Map<String, Object> permissionErrorPayload = Map.of(
                "insufficient_permissions", "Role 'CLAIMS_ANALYST' lacks authority for state transition"
        );
        when(mockService.transitionState("CASE-003", "DECISION_REVIEW")).thenReturn(permissionErrorPayload);

        // Act & Assert: Failure outputs (insufficient_permissions)
        Map<String, Object> resultPermission = handler.executeTransition("CASE-003", "DECISION_REVIEW");
        assertNotNull(resultPermission);
        assertEquals("Role 'CLAIMS_ANALYST' lacks authority for state transition", resultPermission.get("insufficient_permissions"));
    }
}

// Internal interfaces/classes to support the mock architecture
interface DecisionStateTransitionService {
    Map<String, Object> transitionState(String caseId, String action);
}

class DecisionStateTransitionHandler {
    private final DecisionStateTransitionService service;
    DecisionStateTransitionHandler(DecisionStateTransitionService service) {
        this.service = service;
    }
    Map<String, Object> executeTransition(String caseId, String action) {
        return service.transitionState(caseId, action);
    }
}
