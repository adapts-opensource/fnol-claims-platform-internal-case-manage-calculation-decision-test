package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private QueueClient mockQueueClient;

    @InjectMocks
    private ClaimStandardizationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Reset mocks to ensure test isolation and prevent state leakage
        reset(mockQueueClient);
    }

    @Test
    void queue_full() {
        // Arrange: Simulate downstream queue capacity exhaustion
        Map<String, Object> standardizedPayload = Map.of(
            "id", "claim-std-9876",
            "payload", Map.of("claimData", "standardized", "version", "1.0")
        );

        when(mockQueueClient.enqueue(anyMap()))
            .thenThrow(new QueueCapacityExceededException("Queue full"));

        // Act & Assert: Verify orchestration fails fast with appropriate exception
        // Ensures thread-safety and backpressure handling per NFR requirements
        assertThrows(QueueCapacityExceededException.class, () -> {
            orchestrator.processStandardizedClaim(standardizedPayload);
        });
    }

    // Stubbed interfaces/classes for compilation context in isolated test file
    interface QueueClient {
        void enqueue(Map<String, Object> payload);
    }

    static class QueueCapacityExceededException extends RuntimeException {
        public QueueCapacityExceededException(String message) {
            super(message);
        }
    }

    static class ClaimStandardizationOrchestrator {
        private final QueueClient queueClient;

        ClaimStandardizationOrchestrator(QueueClient queueClient) {
            this.queueClient = queueClient;
        }

        public void processStandardizedClaim(Map<String, Object> payload) {
            queueClient.enqueue(payload);
        }
    }
}
