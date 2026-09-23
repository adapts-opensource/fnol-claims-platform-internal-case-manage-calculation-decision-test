package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies graceful handling of rule engine timeouts during insured engagement decision orchestration.
 * Aligns with NFRs: thread_safety, observability, availability, and compliance with mock-only I/O constraints.
 */
@ExtendWith(MockitoExtension.class)
public class RuleEngineTimeoutTest {

    @Mock
    private DecisionOrchestrationService decisionOrchestrationService;

    private DecisionOrchestrationHandler orchestrationHandler;

    @BeforeEach
    void setUp() {
        orchestrationHandler = new DecisionOrchestrationHandler(decisionOrchestrationService);
    }

    @Test
    void rule_engine_timeout() {
        // Arrange: Simulate a rule engine evaluation that exceeds the timeout threshold
        CompletableFuture<String> delayedResult = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(2000); // Simulate prolonged rule evaluation
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "APPROVED";
        });

        when(decisionOrchestrationService.evaluateDecision(any())).thenReturn(delayedResult);

        // Act & Assert: Verify that the orchestration layer throws a TimeoutException
        // when the rule engine fails to respond within the configured window.
        assertThrows(TimeoutException.class, () -> {
            orchestrationHandler.evaluateWithTimeout(delayedResult, 1);
        });

        verify(decisionOrchestrationService, times(1)).evaluateDecision(any());
    }

    /**
     * Minimal interface representing the external rule engine decision service.
     */
    interface DecisionOrchestrationService {
        CompletableFuture<String> evaluateDecision(Object request);
    }

    /**
     * Handler that orchestrates the rule engine call with timeout safeguards.
     * Implements thread-safe blocking with explicit timeout and structured logging readiness.
     */
    static class DecisionOrchestrationHandler {
        private final DecisionOrchestrationService service;

        DecisionOrchestrationHandler(DecisionOrchestrationService service) {
            this.service = service;
        }

        String evaluateWithTimeout(CompletableFuture<String> future, long timeoutSeconds) throws TimeoutException {
            try {
                return future.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                // Structured logging placeholder for observability NFR
                // log.warn("RuleEngineTimeout", "decision", "timeout", "timeoutSeconds", timeoutSeconds);
                throw new TimeoutException("Rule engine evaluation timed out after " + timeoutSeconds + "s");
            } catch (Exception e) {
                throw new RuntimeException("Decision orchestration failed", e);
            }
        }
    }
}
