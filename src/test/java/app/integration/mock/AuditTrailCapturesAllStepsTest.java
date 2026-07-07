package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * JUnit 5 test class for feature: Claim Initiation & Routing:decision:calculation
 * Verifies AuditTrailCapturesAllSteps scenario.
 */
@ExtendWith(MockitoExtension.class)
public class AuditTrailCapturesAllSteps {

    @Mock
    private ClaimDecisionEngine claimDecisionEngine;

    @Mock
    private AuditTrailRecorder auditTrailRecorder;

    @Mock
    private RedisCacheClient redisCacheClient;

    @Mock
    private DynamoDBClient dynamoDBClient;

    private ClaimInitiationService claimInitiationService;

    @BeforeEach
    void setUp() {
        // System Under Test initialization with mocked dependencies
        claimInitiationService = new ClaimInitiationService(
                claimDecisionEngine,
                auditTrailRecorder,
                redisCacheClient,
                dynamoDBClient
        );
    }

    @Test
    void audit_trail_captures_all_steps() {
        // Arrange
        String claimId = "CLM-2024-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("type", "AUTO");
        payload.put("severity", "HIGH");

        // Mock external I/O behavior
        when(redisCacheClient.get(anyString())).thenReturn(null);
        when(dynamoDBClient.getItem(anyString(), anyString())).thenReturn(new HashMap<>());
        when(claimDecisionEngine.calculate(anyMap())).thenReturn(DecisionResult.success(claimId, "APPROVED"));

        // Capture steps for verification
        List<String> capturedSteps = new ArrayList<>();
        doAnswer(invocation -> {
            String step = invocation.getArgument(1);
            capturedSteps.add(step);
            return null;
        }).when(auditTrailRecorder).logStep(anyString(), anyString());

        // Act
        DecisionResult result = claimInitiationService.processClaimInitiation(payload);

        // Assert
        assertNotNull(result);
        assertEquals(claimId, result.getClaimId());
        assertEquals("APPROVED", result.getStatus());

        // Verify audit trail captures all expected steps in order
        assertEquals(4, capturedSteps.size(), "Audit trail must capture all lifecycle steps");
        assertEquals("INITIATION_RECEIVED", capturedSteps.get(0));
        assertEquals("ROUTING_DECIDED", capturedSteps.get(1));
        assertEquals("CALCULATION_STARTED", capturedSteps.get(2));
        assertEquals("CALCULATION_COMPLETED", capturedSteps.get(3));

        // Verify no unexpected interactions
        verifyNoMoreInteractions(auditTrailRecorder);
        verify(redisCacheClient, times(1)).get(anyString());
        verify(dynamoDBClient, times(1)).getItem(anyString(), anyString());
        verify(claimDecisionEngine, times(1)).calculate(anyMap());
    }

    // Simulated internal classes for compilation context
    static class DecisionResult {
        private final String claimId;
        private final String status;

        private DecisionResult(String claimId, String status) {
            this.claimId = claimId;
            this.status = status;
        }

        static DecisionResult success(String claimId, String status) {
            return new DecisionResult(claimId, status);
        }

        public String getClaimId() { return claimId; }
        public String getStatus() { return status; }
    }
}
