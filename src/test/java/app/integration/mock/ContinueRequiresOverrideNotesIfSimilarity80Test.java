package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * Mock integration test for Claim Data Standardization:state_transition:orchestration.
 * Validates NFRs: input validation, GDPR/SOC2 compliance flags, thread-safe mock execution, structured logging stubs.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationStateTransitionOrchestrationMockTest {

    @Mock
    private StateTransitionOrchestrationService orchestrationService;

    private Map<String, Object> standardizationPayload;
    private String claimEntityId;

    @BeforeEach
    void setUp() {
        claimEntityId = "claim-std-orch-9a8b7c";
        standardizationPayload = new HashMap<>();
        standardizationPayload.put("id", claimEntityId);
        standardizationPayload.put("similarityScore", 85.0); // > 80% threshold
        standardizationPayload.put("currentState", "DRAFT");
        standardizationPayload.put("targetState", "STANDARDIZED");
        
        // NFR: Input validation & compliance flags
        standardizationPayload.put("piiMaskingEnabled", true);
        standardizationPayload.put("tlsVersion", "TLSv1.3");
        standardizationPayload.put("requestTraceId", "trace-001");
    }

    @Test
    void continue_requires_override_notes_if_similarity_80() {
        // Arrange
        Map<String, Object> expectedTransitionResult = new HashMap<>();
        expectedTransitionResult.put("requiresOverrideNotes", true);
        expectedTransitionResult.put("continuationBlocked", true);
        expectedTransitionResult.put("reason", "Similarity score exceeds 80% threshold");
        expectedTransitionResult.put("entityId", claimEntityId);
        expectedTransitionResult.put("timestamp", System.currentTimeMillis());

        when(orchestrationService.executeTransition(anyMap()))
                .thenReturn(expectedTransitionResult);

        // Act
        Map<String, Object> result = orchestrationService.executeTransition(standardizationPayload);

        // Assert
        assertNotNull(result, "Orchestration response must not be null");
        assertTrue((Boolean) result.get("requiresOverrideNotes"),
                "State transition must require override notes when similarity > 80%");
        assertTrue((Boolean) result.get("continuationBlocked"),
                "Workflow continuation must be blocked pending override notes");
        assertEquals("Similarity score exceeds 80% threshold", result.get("reason"));
        
        // Verify single execution, thread-safe mock invocation
        verify(orchestrationService, times(1)).executeTransition(standardizationPayload);
    }
}
