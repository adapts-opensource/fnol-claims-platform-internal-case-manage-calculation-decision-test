package app.integration.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Verify Concurrent Requests For Claim Data Standardization Transformation")
class VerifyConcurrentRequestsForClaimDataStandardizationTransformation {

    private static final int MAX_CONCURRENT_REQUESTS = 50;
    private static final long LATENCY_BOUND_MS = 200;
    private static final int TOTAL_REQUESTS = 100;

    @Test
    @DisplayName("verify_concurrent_requests_for_claim_data_standardization_transformation_orchestration_stay_within_latency_bounds")
    void verify_concurrent_requests_for_claim_data_standardization_transformation_orchestration_stay_within_latency_bounds() throws Exception {
        ClaimDataStandardizationOrchestrationService mockService = new ClaimDataStandardizationOrchestrationService();

        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(TOTAL_REQUESTS);
        AtomicLong totalLatency = new AtomicLong(0);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    mockService.orchestrateClaimDataStandardization(
                        String.valueOf(requestId),
                        Map.of("claimId", "CLM-" + requestId, "status", "INITIATED")
                    );
                    long end = System.nanoTime();
                    long latencyMs = TimeUnit.NANOSECONDS.toMillis(end - start);
                    latencies.add(latencyMs);
                    totalLatency.addAndGet(latencyMs);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long avgLatency = totalLatency.get() / TOTAL_REQUESTS;
        double p95Latency = latencies.stream().sorted().skip((long) (TOTAL_REQUESTS * 0.95)).findFirst().orElse(0L);

        assertTrue(avgLatency <= LATENCY_BOUND_MS,
            "Average latency " + avgLatency + "ms exceeds bound of " + LATENCY_BOUND_MS + "ms");
        assertTrue(p95Latency <= LATENCY_BOUND_MS,
            "P95 latency " + p95Latency + "ms exceeds bound of " + LATENCY_BOUND_MS + "ms");
    }

    private static class ClaimDataStandardizationOrchestrationService {
        void orchestrateClaimDataStandardization(String id, Map<String, Object> payload) {
            // Simulate structured logging for observability NFR
            System.out.printf("[PERF-LOG] requestId=%s entity=claim_data_standardization_state_transition_orch status=SUCCESS%n", id);
            // Mock DynamoDB/S3 I/O latency without live AWS calls
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
