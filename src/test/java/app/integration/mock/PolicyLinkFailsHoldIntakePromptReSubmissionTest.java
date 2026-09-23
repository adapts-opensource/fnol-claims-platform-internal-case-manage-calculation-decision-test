package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Minimal domain interfaces to satisfy mock compilation context
interface PolicyLinkValidator {
    void validate(String policyId) throws PolicyLinkException;
}

interface ClaimDataStore {
    void persist(Map<String, Object> item);
}

interface StateTransitionOrchestrator {
    Map<String, Object> orchestrateTransition(String claimId, Map<String, Object> payload);
}

class PolicyLinkException extends RuntimeException {
    PolicyLinkException(String message) { super(message); }
}

@ExtendWith(MockitoExtension.class)
class PolicyLinkFailsHoldIntakePromptReSubmissionTest {

    @Mock
    private PolicyLinkValidator policyLinkValidator;

    @Mock
    private ClaimDataStore claimDataStore;

    @Mock
    private StateTransitionOrchestrator orchestrator;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    @Test
    void policy_link_fails_hold_intake_prompt_re_submission() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> inputPayload = Map.of(
            "id", claimId,
            "status", "INTAKE_INITIATED",
            "policyId", "POL-FAIL-001",
            "promptResubmission", false,
            "policyLinked", false
        );

        // Mock external policy service to fail
        when(policyLinkValidator.validate(anyString())).thenThrow(new PolicyLinkException("Policy link validation failed: policy not found"));

        // Act
        Map<String, Object> resultPayload = orchestrator.orchestrateTransition(claimId, inputPayload);

        // Assert state transition
        assertEquals("HOLD_INTAKE", resultPayload.get("status"));
        assertTrue((Boolean) resultPayload.get("promptResubmission"));
        assertFalse((Boolean) resultPayload.get("policyLinked"));

        // Verify external I/O interactions
        verify(policyLinkValidator, times(1)).validate("POL-FAIL-001");
        verify(claimDataStore, times(1)).persist(payloadCaptor.capture());

        // Verify persisted payload matches expected state
        Map<String, Object> savedPayload = payloadCaptor.getValue();
        assertEquals("HOLD_INTAKE", savedPayload.get("status"));
        assertTrue((Boolean) savedPayload.get("promptResubmission"));
        assertEquals("POL-FAIL-001", savedPayload.get("policyId"));
    }
}
