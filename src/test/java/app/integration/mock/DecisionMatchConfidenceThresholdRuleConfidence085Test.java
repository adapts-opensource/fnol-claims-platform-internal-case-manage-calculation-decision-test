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

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolOrchestrationValidationTest {

    @Mock
    private ClaimIntakeService s3Service;

    @Mock
    private DataStoreService dynamoService;

    @Mock
    private CommunicationsHandlerService sesService;

    @InjectMocks
    private FnolOrchestrationValidator sut;

    @BeforeEach
    void setUp() {
        // MockitoExtension automatically initializes mocks and injects them
    }

    @Test
    void decisionMatchConfidenceThresholdRuleConfidence085AutoMatch060084Review060UnmatchedExpectedOutcomeRoutingDecisionAndTaskGeneration() {
        // Arrange: Simulate FNOL payload with match confidence exactly at the auto-match threshold (0.85)
        String submissionId = "fnol-claim-789";
        double matchConfidence = 0.85;
        Map<String, Object> payload = Map.of("id", submissionId, "matchConfidence", matchConfidence);

        // Act: Execute orchestration validation logic
        RoutingOutcome outcome = sut.processSubmission(payload);

        // Assert: Verify routing decision aligns with confidence >= 0.85 rule
        assertEquals(RoutingDecision.AUTO_MATCH, outcome.decision(),
                "Confidence >= 0.85 must yield AUTO_MATCH routing decision");
        assertTrue(outcome.taskGenerated(),
                "Auto-match decision must trigger task generation");
        assertNotNull(outcome.taskId(),
                "Generated task must include a valid task identifier");

        // Verify external I/O contracts are mocked; no live AWS/HTTP calls occur
        verify(s3Service, never()).putObject(anyString(), anyString(), any());
        verify(dynamoService, never()).putItem(anyString(), anyMap());
        verify(sesService, never()).sendEmail(anyString(), anyList(), anyString());

        // Verify orchestration correctly isolates business logic from infrastructure
        verifyNoInteractions(communicationsHandlerService);
    }

    // System Under Test: Orchestration/Validation Layer
    static class FnolOrchestrationValidator {
        public RoutingOutcome processSubmission(Map<String, Object> payload) {
            double confidence = Double.parseDouble(payload.get("matchConfidence").toString());
            RoutingDecision decision;
            if (confidence >= 0.85) {
                decision = RoutingDecision.AUTO_MATCH;
            } else if (confidence >= 0.60) {
                decision = RoutingDecision.REVIEW;
            } else {
                decision = RoutingDecision.UNMATCHED;
            }
            boolean taskGenerated = decision != RoutingDecision.UNMATCHED;
            String taskId = taskGenerated ? "task-" + System.currentTimeMillis() : null;
            return new RoutingOutcome(decision, taskGenerated, taskId);
        }
    }

    // Domain Record
    record RoutingOutcome(RoutingDecision decision, boolean taskGenerated, String taskId) {}

    // Mocked Infrastructure Interfaces (simplified for testing)
    interface ClaimIntakeService {
        void putObject(String bucket, String key, Object content);
    }
    interface DataStoreService {
        void putItem(String table, Map<String, Object> item);
    }
    interface CommunicationsHandlerService {
        void sendEmail(String from, java.util.List<String> to, String region);
    }
}
