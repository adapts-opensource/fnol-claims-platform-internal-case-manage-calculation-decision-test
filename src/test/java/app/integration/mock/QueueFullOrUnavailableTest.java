package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QueueFullOrUnavailableTest {

    @Mock
    private MessageQueueService messageQueueService;

    private DecisionOrchestrator decisionOrchestrator;

    @BeforeEach
    void setUp() {
        decisionOrchestrator = new DecisionOrchestrator(messageQueueService);
    }

    @Test
    void queue_full_or_unavailable() {
        // Arrange
        String engagementId = "insured-eng-789";
        QueueUnavailableException queueFailure = new QueueUnavailableException("Queue full or unavailable");
        when(messageQueueService.submit(any())).thenThrow(queueFailure);

        // Act
        DecisionResult result = decisionOrchestrator.processDecision(engagementId);

        // Assert
        assertNotNull(result);
        assertEquals(DecisionStatus.QUEUE_UNAVAILABLE, result.getStatus());
        assertTrue(result.isRetryable());
        assertEquals(engagementId, result.getEngagementId());
        verify(messageQueueService).submit(any());
    }

    // Minimal test doubles for feature context: Insured Engagement & Tracking:orchestration:decision
    interface MessageQueueService {
        void submit(Object payload);
    }

    class DecisionOrchestrator {
        private final MessageQueueService queueService;

        DecisionOrchestrator(MessageQueueService queueService) {
            this.queueService = queueService;
        }

        DecisionResult processDecision(String engagementId) {
            try {
                queueService.submit(engagementId);
                return new DecisionResult(engagementId, DecisionStatus.QUEUED);
            } catch (QueueUnavailableException e) {
                return new DecisionResult(engagementId, DecisionStatus.QUEUE_UNAVAILABLE);
            }
        }
    }

    record DecisionResult(String engagementId, DecisionStatus status) {
        public boolean isRetryable() {
            return status == DecisionStatus.QUEUE_UNAVAILABLE;
        }
    }

    enum DecisionStatus {
        QUEUED, QUEUE_UNAVAILABLE
    }

    static class QueueUnavailableException extends RuntimeException {
        QueueUnavailableException(String message) {
            super(message);
        }
    }
}
