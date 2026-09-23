package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 test class for Claim Data Standardization:transformation:orchestration.
 * Verifies agent validation logic within the orchestration layer using mocked infra contracts.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationStateTransitionOrchTest {

    @Mock
    private RulesTriageService rulesTriageService;

    @InjectMocks
    private ClaimDataStandardizationOrchestrator orchestrator;

    @Test
    void agent_id_must_be_active_and_licensed() {
        // Given: Valid agent ID with active status and valid license
        String validAgentId = "AGENT_12345";
        Map<String, Object> payload = Map.of(
                "id", "claim_001",
                "payload", Map.of(
                        "agent_id", validAgentId,
                        "claim_type", "AUTO_COLLISION",
                        "incident_date", "2023-10-27"
                )
        );

        when(rulesTriageService.validateAgent(anyString()))
                .thenReturn(AgentValidationResult.activeAndLicensed(validAgentId));

        // When: Orchestration processes the payload
        // Then: No exception should be thrown; validation passes
        assertDoesNotThrow(() -> orchestrator.transformAndValidate(payload));
        verify(rulesTriageService, times(1)).validateAgent(validAgentId);
    }

    @Test
    void agent_id_must_be_active_and_licensed_inactive_agent_throws_validation_error() {
        // Given: Agent exists but is inactive
        String inactiveAgentId = "AGENT_99999";
        Map<String, Object> payload = Map.of(
                "id", "claim_002",
                "payload", Map.of("agent_id", inactiveAgentId)
        );

        when(rulesTriageService.validateAgent(anyString()))
                .thenReturn(AgentValidationResult.inactive(inactiveAgentId));

        // When & Then: Orchestration must reject inactive agents
        Exception exception = assertThrows(ValidationException.class,
                () -> orchestrator.transformAndValidate(payload));
        assertTrue(exception.getMessage().contains("Agent must be active"));
        verify(rulesTriageService, times(1)).validateAgent(inactiveAgentId);
    }

    @Test
    void agent_id_must_be_active_and_licensed_unlicensed_agent_throws_validation_error() {
        // Given: Agent is active but license is lapsed
        String unlicensedAgentId = "AGENT_88888";
        Map<String, Object> payload = Map.of(
                "id", "claim_003",
                "payload", Map.of("agent_id", unlicensedAgentId)
        );

        when(rulesTriageService.validateAgent(anyString()))
                .thenReturn(AgentValidationResult.unlicensed(unlicensedAgentId));

        // When & Then: Orchestration must reject unlicensed agents
        Exception exception = assertThrows(ValidationException.class,
                () -> orchestrator.transformAndValidate(payload));
        assertTrue(exception.getMessage().contains("Agent must be licensed"));
        verify(rulesTriageService, times(1)).validateAgent(unlicensedAgentId);
    }

    @Test
    void agent_id_must_be_active_and_licensed_missing_agent_id_throws_validation_error() {
        // Given: Payload missing agent_id
        Map<String, Object> payload = Map.of(
                "id", "claim_004",
                "payload", Map.of("claim_type", "AUTO_COLLISION")
        );

        // When & Then: Orchestration must enforce required field validation
        Exception exception = assertThrows(ValidationException.class,
                () -> orchestrator.transformAndValidate(payload));
        assertTrue(exception.getMessage().contains("agent_id"));
        verifyNoInteractions(rulesTriageService);
    }

    /**
     * Mock service interface representing Rules & Triage Service DynamoDB lookup.
     * Simulates external I/O contract for agent status validation.
     */
    private interface RulesTriageService {
        AgentValidationResult validateAgent(String agentId);
    }

    /**
     * Record representing validation result from Rules & Triage Service.
     */
    private record AgentValidationResult(
            String agentId,
            boolean active,
            boolean licensed
    ) {
        static AgentValidationResult activeAndLicensed(String agentId) {
            return new AgentValidationResult(agentId, true, true);
        }

        static AgentValidationResult inactive(String agentId) {
            return new AgentValidationResult(agentId, false, true);
        }

        static AgentValidationResult unlicensed(String agentId) {
            return new AgentValidationResult(agentId, true, false);
        }
    }

    /**
     * Custom exception for validation failures during orchestration.
     */
    private static class ValidationException extends RuntimeException {
        ValidationException(String message) {
            super(message);
        }
    }
}
