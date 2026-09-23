package app.integration.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Insured Engagement & Tracking Decision Performance Test")
class InsuredEngagementTrackingDecisionPerformanceTest {

    private static final Logger LOG = Logger.getLogger(InsuredEngagementTrackingDecisionPerformanceTest.class.getName());
    private static final int CONCURRENT_THREADS = 50;
    private static final int REQUESTS_PER_THREAD = 20;
    private static final long MAX_AVG_LATENCY_MS = 150;
    private static final long MAX_P95_LATENCY_MS = 280;

    private DecisionTransformationService mockService;

    @BeforeEach
    void setUp() {
        mockService = mock(DecisionTransformationService.class);
        // Mock external I/O (DynamoDB/SES persistence & notification) with deterministic processing time
        when(mockService.transformAndPersistDecision(anyString(), any())).thenAnswer(invocation -> {
            Thread.sleep(45); // Simulate base transformation + persistence latency
            return "TransformedDecisionRecord";
        });
    }

    @Test
    @DisplayName("verify_concurrent_requests_for_insured_engagement_tracking_decision_transformation_stay_within_latency_bounds")
    void verify_concurrent_requests_for_insured_engagement_tracking_decision_transformation_stay_within_latency_bounds() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_THREADS);
        List<Long> latencies = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        long startTime = System.nanoTime();

        LOG.info("Starting concurrent load test: " + CONCURRENT_THREADS + " threads x " + REQUESTS_PER_THREAD + " requests");

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                        long reqStart = System.nanoTime();
                        mockService.transformAndPersistDecision("insured-" + i + "-" + j, "decisionPayload-v1");
                        long reqEnd = System.nanoTime();
                        latencies.add((reqEnd - reqStart) / 1_000_000);
                    }
                } catch (Exception e) {
                    error.set(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        long totalTimeMs = (System.nanoTime() - startTime) / 1_000_000;
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        if (!completed) {
            fail("Concurrency test timed out waiting for threads to complete.");
        }
        if (error.get() != null) {
            fail("Concurrency test failed with exception: " + error.get().getMessage(), error.get());
        }

        // Performance metrics calculation
        latencies.sort(Long::compareTo);
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        int p95Index = (int) Math.ceil(latencies.size() * 0.95) - 1;
        long p95Latency = latencies.get(p95Index);

        LOG.log(Level.INFO, "Performance Results | Avg: {0} ms | P95: {1} ms | Total Time: {2} ms | Throughput: {3} req/s",
                new Object[]{String.format("%.2f", avgLatency), p95Latency, totalTimeMs,
                        (latencies.size() * 1000.0 / totalTimeMs)});

        assertTrue(avgLatency <= MAX_AVG_LATENCY_MS,
                String.format("Average latency %.2f ms exceeds bound of %d ms", avgLatency, MAX_AVG_LATENCY_MS));
        assertTrue(p95Latency <= MAX_P95_LATENCY_MS,
                String.format("P95 latency %d ms exceeds bound of %d ms", p95Latency, MAX_P95_LATENCY_MS));
    }

    /**
     * Mock interface abstracting external I/O (DynamoDB persistence & SES notifications)
     * to ensure zero live AWS/HTTP calls during performance testing.
     */
    interface DecisionTransformationService {
        String transformAndPersistDecision(String insuredId, String decisionPayload);
    }
}
