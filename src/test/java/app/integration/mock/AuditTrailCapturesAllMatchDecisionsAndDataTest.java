package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditTrailCapturesAllMatchDecisionsAndDataTest {

    @Mock
    private AuditTrailCaptureService auditTrailCaptureService;

    @Mock
    private DecisionTransformationEngine decisionTransformationEngine;

    private ClaimDataStandardizationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ClaimDataStandardizationProcessor(decisionTransformationEngine, auditTrailCaptureService);
    }

    @Test
    void auditTrailCapturesAllMatchDecisionsAndData() {
        // Arrange: Prepare claim data and expected match decisions
        String claimId = "CLM-12345";
        Map<String, Object> inputPayload = Map.of("claimId", claimId, "payload", Map.of("status", "pending"));
        List<Map<String, Object>> expectedMatchDecisions = List.of(
            Map.of("ruleId", "RULE-001", "match", true, "score", 0.95),
            Map.of("ruleId", "RULE-002", "match", false, "score", 0.40),
            Map.of("ruleId", "RULE-003", "match", true, "score", 0.88)
        );

        // Mock external decision engine transformation (simulates RulesEngineDecisionService)
        when(decisionTransformationEngine.transform(anyMap())).thenReturn(expectedMatchDecisions);

        // Act: Execute claim data standardization transformation
        processor.processClaimData(claimId, inputPayload);

        // Assert: Verify audit trail captured all match decisions and data
        ArgumentCaptor<Map<String, Object>> auditDataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditTrailCaptureService, times(1)).captureAuditTrail(eq(claimId), auditDataCaptor.capture());

        Map<String, Object> capturedData = auditDataCaptor.getValue();
        assertNotNull(capturedData, "Audit trail data should not be null");
        assertEquals(claimId, capturedData.get("claimId"), "Claim ID must match input");
        assertEquals(expectedMatchDecisions, capturedData.get("matchDecisions"), "All match decisions must be captured");
        assertEquals(3, ((List<?>) capturedData.get("matchDecisions")).size(), "Captured decision count must match expected");
        assertTrue(((List<?>) capturedData.get("matchDecisions")).stream()
            .allMatch(d -> d instanceof Map && ((Map<?, ?>) d).containsKey("ruleId")),
            "Each decision must contain a ruleId");
    }
}
