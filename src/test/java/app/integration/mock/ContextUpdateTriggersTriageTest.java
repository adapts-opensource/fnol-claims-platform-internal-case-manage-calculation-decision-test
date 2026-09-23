package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.HashMap;

/**
 * Verifies that context updates in the Multi-Channel FNOL Submission workflow
 * correctly trigger triage state transitions.
 * NFR Alignment: Thread-safe mocks, GDPR/SOC2 PII masking in payloads, structured logging via service layer.
 */
@ExtendWith(MockitoExtension.class)
public class ContextUpdateTriggersTriageTest {

    @Mock
    private ValidationEngine validationEngine;

    @Mock
    private StateTransitionService stateTransitionService;

    @InjectMocks
    private ContextUpdateOrchestrator contextUpdateOrchestrator;

    private String testEntityId;
    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testEntityId = "fnol-ctx-update-001";
        testPayload = new HashMap<>();
        testPayload.put("channel", "WEB");
        testPayload.put("claimType", "AUTO");
        testPayload.put("severity", "LOW");
        testPayload.put("status", "SUBMITTED");
        testPayload.put("piiMasked", true); // GDPR/SOC2 compliance: PII masked before I/O
    }

    @Test
    void contextUpdateTriggersTriage() {
        // Arrange: Mock external I/O contracts (S3, SES, DynamoDB) via service layer
        when(validationEngine.validateInput(anyMap())).thenReturn(true);
        when(stateTransitionService.persistStateTransition(anyString(), anyMap())).thenReturn("TRANSITION_SUCCESS");
        when(stateTransitionService.triggerTriage(anyString())).thenReturn(true);

        // Act: Execute context update orchestration for entity multi_channel_fnol_submission_state_transition_c
        boolean triageTriggered = contextUpdateOrchestrator.processContextUpdate(testEntityId, testPayload);

        // Assert: Verify triage was triggered and mocked I/O interactions occurred exactly once
        assertTrue(triageTriggered, "Context update should successfully trigger triage");
        verify(validationEngine).validateInput(testPayload);
        verify(stateTransitionService).persistStateTransition(eq(testEntityId), anyMap());
        verify(stateTransitionService).triggerTriage(testEntityId);
    }
}
