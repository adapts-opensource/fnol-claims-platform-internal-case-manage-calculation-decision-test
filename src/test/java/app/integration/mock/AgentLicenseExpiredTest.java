package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationOrchestrationTest {

    @Mock
    private ClaimDataStandardizationOrchestrator mockOrchestrator;

    private ClaimDataStandardizationStateTransitionOrch sut;

    @BeforeEach
    void setUp() {
        sut = new ClaimDataStandardizationStateTransitionOrch(mockOrchestrator);
    }

    @Test
    void agent_license_expired() {
        // Arrange
        String id = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("agentLicense", Map.of("status", "EXPIRED", "expiryDate", "2023-01-01"));
        payload.put("claimId", "CLM-12345");
        payload.put("standardizedData", new HashMap<>());

        doNothing().when(mockOrchestrator).validateAgentLicense(payload);
        when(mockOrchestrator.determineState(anyMap())).thenReturn("AGENT_LICENSE_EXPIRED");

        // Act
        Map<String, Object> result = sut.processPayload(id, payload);

        // Assert
        assertNotNull(result, "Result map should not be null");
        assertEquals("AGENT_LICENSE_EXPIRED", result.get("currentState"), "State should reflect expired license");
        assertEquals(id, result.get("id"), "ID should match input");
        verify(mockOrchestrator).validateAgentLicense(payload);
        verify(mockOrchestrator).determineState(payload);
    }
}

// Minimal SUT for orchestration logic
class ClaimDataStandardizationStateTransitionOrch {
    private final ClaimDataStandardizationOrchestrator orchestrator;

    ClaimDataStandardizationStateTransitionOrch(ClaimDataStandardizationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    Map<String, Object> processPayload(String id, Map<String, Object> payload) {
        orchestrator.validateAgentLicense(payload);
        String state = orchestrator.determineState(payload);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("currentState", state);
        return result;
    }
}

// Mocked external I/O contract interface
interface ClaimDataStandardizationOrchestrator {
    void validateAgentLicense(Map<String, Object> payload);
    String determineState(Map<String, Object> payload);
}
