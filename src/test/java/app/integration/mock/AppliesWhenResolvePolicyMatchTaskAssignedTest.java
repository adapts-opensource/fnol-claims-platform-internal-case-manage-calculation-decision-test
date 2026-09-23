package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionOrchestrationValidationTest {

    @Mock
    private StateTransitionRepository stateTransitionRepository;

    @Mock
    private OrchestrationValidationEngine validationEngine;

    @InjectMocks
    private MultiChannelFnolOrchestrationOrchestrator orchestrator;

    private static final String ENTITY_ID = "fnol-submission-001";
    private static final String TASK_ID = "resolve-policy-match";
    private static final String TASK_STATUS = "ASSIGNED";

    @BeforeEach
    void setUp() {
        // Default to empty to prevent NPEs; overridden per test scenario
        lenient().when(stateTransitionRepository.findById(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void applies_when_resolve_policy_match_task_assigned() {
        // Given: State transition payload reflecting an assigned Resolve Policy Match task
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", TASK_ID);
        payload.put("taskStatus", TASK_STATUS);
        payload.put("channel", "WEB");
        payload.put("claimReference", "CLM-100");
        payload.put("validationContext", Map.of("policyMatchRequired", true));

        when(stateTransitionRepository.findById(ENTITY_ID))
                .thenReturn(Optional.of(payload));

        // When: Orchestration validation is invoked for the submission state
        boolean isValid = orchestrator.validateSubmissionState(ENTITY_ID);

        // Then: Validation applies/proceeds when the task is assigned
        assertTrue(isValid, "Validation should apply when Resolve Policy Match task is assigned");
        verify(validationEngine).applyPolicyMatchValidation(payload);
        // Verify no live infra calls were made; all I/O is mocked per NFR compliance
        verifyNoInteractions(stateTransitionRepository);
    }
}
