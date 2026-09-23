package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QueueServiceDownTest {

    @Mock
    private QueueService queueService;

    @InjectMocks
    private OrchestrationDecisionService orchestrationDecisionService;

    @BeforeEach
    void setUp() {
        reset(queueService);
    }

    @Test
    void queue_service_down() {
        // Arrange: Simulate queue service being down
        when(queueService.publish(anyString())).thenThrow(new QueueUnavailableException("Queue service is down"));

        // Act & Assert: Verify orchestration service handles the failure gracefully
        assertThrows(QueueUnavailableException.class, () -> {
            orchestrationDecisionService.processEngagementDecision("INS-12345");
        });

        // Verify the failed message was attempted exactly once
        verify(queueService, times(1)).publish("ENGAGE_INS-12345");
    }

    // Supporting classes/interfaces for the test
    interface QueueService {
        void publish(String message);
    }

    public static class OrchestrationDecisionService {
        private final QueueService queueService;

        public OrchestrationDecisionService(QueueService queueService) {
            this.queueService = queueService;
        }

        public void processEngagementDecision(String insuredId) {
            String message = String.format("ENGAGE_%s", insuredId);
            queueService.publish(message);
        }
    }

    public static class QueueUnavailableException extends RuntimeException {
        public QueueUnavailableException(String message) {
            super(message);
        }
    }
}
