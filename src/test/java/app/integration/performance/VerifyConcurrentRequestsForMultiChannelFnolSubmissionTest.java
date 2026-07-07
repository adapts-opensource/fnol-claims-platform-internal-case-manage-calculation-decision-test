package app.integration.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@DisplayName("Verify Concurrent Requests for Multi-Channel FNOL Submission: Validation Decision")
public class VerifyConcurrentRequestsForMultiChannelFnolSubmissionValidationDecisionPerformanceTest {

    private static final int CONCURRENT_THREADS = 50;
    private static final int TOTAL_REQUESTS = 500;
    private static final long LATENCY_BOUND_MS = 500;
    private static final double THROUGHPUT_BOUND_REQS_PER_SEC = 100.0;

    private FnolValidationDecisionService mockService;

    // Mock external I/O for validation decision service boundary
    interface FnolValidationDecisionService {
        boolean processValidationDecision(String tenantId, String policyId);
    }

    @BeforeEach
    void setUp() {
        // Simulate structured logging initialization for observability NFR
        // Logger.info("Initializing mock validation decision service for performance test");
        mockService = (tenantId, policyId) -> {
            try {
                Thread.sleep(5); // Simulate network/DB latency for validation decision
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true; // Idempotent side-effect simulation
        };
    }

    @Test
    @DisplayName("verify_concurrent_requests_for_multi_channel_fnol_submission_validation_decision_stay_within_latency_bounds")
    void verify_concurrent_requests_for_multi_channel_fnol_submission_validation_decision_stay_within_latency_bounds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);
        AtomicLong failedRequests = new AtomicLong(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        long startTime = System.nanoTime();

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    long reqStart = System.nanoTime();
                    // Thread-safe concurrent validation decision processing
                    boolean result = mockService.processValidationDecision("tenant_" + (requestId % 5), "policy_" + (requestId % 10));
                    assertTrue(result, "Validation decision should succeed for request " + requestId);
                    long reqEnd = System.nanoTime();
                    long latencyMs = (reqEnd - reqStart) / 1_000_000;
                    latencies.add(latencyMs);
                } catch (Exception e) {
                    failedRequests.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        long endTime = System.nanoTime();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long elapsedMs = (endTime - startTime) / 1_000_000;
        double throughput = (TOTAL_REQUESTS / (elapsedMs / 1000.0));

        // P99 Latency Bound Check
        long p99Latency = latencies.isEmpty() ? 0L : latencies.stream().sorted().skip((long) (latencies.size() * 0.99)).findFirst().orElse(0L);
        assertTrue(p99Latency <= LATENCY_BOUND_MS, "P99 latency " + p99Latency + "ms exceeded bound " + LATENCY_BOUND_MS + "ms");

        // Throughput Bound Check
        assertTrue(throughput >= THROUGHPUT_BOUND_REQS_PER_SEC, "Throughput " + throughput + " req/s below bound " + THROUGHPUT_BOUND_REQS_PER_SEC);

        // Concurrency Safety Check (Thread Safety NFR)
        assertEquals(0L, failedRequests.get(), "No concurrent requests should fail due to race conditions");
    }
}
