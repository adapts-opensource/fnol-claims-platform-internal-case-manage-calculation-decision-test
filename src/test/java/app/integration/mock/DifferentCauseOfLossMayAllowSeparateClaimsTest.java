package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionStateTransitionCalculationMockTest {

    @Mock
    private FnolSubmissionRepository submissionRepository;

    @Mock
    private StateTransitionCalculator calculator;

    private MultiChannelFnolSubmissionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MultiChannelFnolSubmissionProcessor(submissionRepository, calculator);
    }

    @Test
    void different_cause_of_loss_may_allow_separate_claims() {
        // Given: Submissions with different causes of loss
        Map<String, Object> payload1 = new HashMap<>();
        payload1.put("causeOfLoss", "Collision");
        payload1.put("channel", "mobile");

        Map<String, Object> payload2 = new HashMap<>();
        payload2.put("causeOfLoss", "Theft");
        payload2.put("channel", "web");

        when(submissionRepository.findById("sub-001")).thenReturn(Map.of("id", "sub-001", "payload", payload1));
        when(submissionRepository.findById("sub-002")).thenReturn(Map.of("id", "sub-002", "payload", payload2));

        // Mock calculator to return separate claim IDs based on cause of loss
        when(calculator.calculateStateTransition(payload1)).thenReturn(Map.of("claimId", "CLM-1001", "state", "OPEN"));
        when(calculator.calculateStateTransition(payload2)).thenReturn(Map.of("claimId", "CLM-1002", "state", "OPEN"));

        // When: Processing submissions
        Map<String, Object> result1 = processor.processSubmission("sub-001");
        Map<String, Object> result2 = processor.processSubmission("sub-002");

        // Then: Separate claims are generated
        assertNotNull(result1.get("claimId"));
        assertNotNull(result2.get("claimId"));
        assertNotEquals(result1.get("claimId"), result2.get("claimId"));
        assertEquals("CLM-1001", result1.get("claimId"));
        assertEquals("CLM-1002", result2.get("claimId"));
        assertEquals("OPEN", result1.get("state"));
        assertEquals("OPEN", result2.get("state"));
    }

    // Minimal implementation to compile the test without external dependencies
    private static class MultiChannelFnolSubmissionProcessor {
        private final FnolSubmissionRepository repository;
        private final StateTransitionCalculator calculator;

        public MultiChannelFnolSubmissionProcessor(FnolSubmissionRepository repository, StateTransitionCalculator calculator) {
            this.repository = repository;
            this.calculator = calculator;
        }

        public Map<String, Object> processSubmission(String id) {
            Map<String, Object> submission = repository.findById(id);
            Map<String, Object> payload = (Map<String, Object>) submission.get("payload");
            return calculator.calculateStateTransition(payload);
        }
    }

    private interface FnolSubmissionRepository {
        Map<String, Object> findById(String id);
    }

    private interface StateTransitionCalculator {
        Map<String, Object> calculateStateTransition(Map<String, Object> payload);
    }
}
