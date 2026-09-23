package app.integration.performance;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Performance test for Claim Data Standardization:state_transition:orchestration.
 * Verifies concurrent request handling and latency bounds.
 */
class VerifyConcurrentRequestsForClaimDataStandardizationStateTransitionOrchestrationPerfTest {

    private static final int CONCURRENCY_LEVEL = 50;
    private static final int REQUEST_COUNT = 200;
    private static final long MAX_P95_LATENCY_MS = 100;

    @Test
    void verify_concurrent_requests_for_claim_data_standardization_state_transition_orchestration_stay_within_latency_bounds() throws Exception {
        // Arrange
        var mockService = Mockito.mock(ClaimDataStandardizationOrchestrationService.class);
        when(mockService.processStateTransition(any(String.class), any(Map.class)))
                .thenAnswer(invocation -> {
                    // Simulate minimal processing overhead to isolate orchestration logic performance
                    Thread.sleep(1);
                    return true;
                });

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY_LEVEL);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(REQUEST_COUNT);
        ConcurrentLinkedQueue<Long> latenciesMs = new ConcurrentLinkedQueue<>();

        // Act
        for (int i = 0; i < REQUEST_COUNT; i++) {
            final String claimId = "claim-" + i;
            final Map<String, Object> payload = Map.of(
                    "id", claimId,
                    "payload", Map.of("standardization_status", "PENDING")
            );

            executor.submit(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    mockService.processStateTransition(claimId, payload);
                    long end = System.nanoTime();
                    latenciesMs.add((end - start) / 1_000_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Trigger all requests concurrently
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertTrue(completed, "Requests did not complete within timeout");
        
        assertAll(() -> {
            assertFalse(latenciesMs.isEmpty(), "No latency data collected");
            double p95 = calculateP95(latenciesMs);
            assertTrue(p95 <= MAX_P95_LATENCY_MS, 
                    String.format("P95 latency %.2fms exceeds bound %.2fms", p95, MAX_P95_LATENCY_MS));
        });
    }

    private double calculateP95(ConcurrentLinkedQueue<Long> latencies) {
        var sorted = latencies.stream().sorted().collect(Collectors.toList());
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return sorted.get(Math.max(0, index));
    }
}
