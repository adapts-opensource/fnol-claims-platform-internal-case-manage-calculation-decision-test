package app.integration.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class InternalCaseManagementCalculationDecisionPerformanceTest {

    private static final int NOMINAL_LOAD_ITERATIONS = 100;
    private static final long MAX_RESPONSE_TIME_MS = 500;
    private static final int CONCURRENT_THREADS = 10;

    private MockDecisionService decisionService;

    private static class MockDecisionService {
        public long calculateDecision() {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 42L;
        }
    }

    @BeforeEach
    void setUp() {
        decisionService = new MockDecisionService();
    }

    @Test
    @DisplayName("measure_response_time_for_internal_case_management_calculation_decision_under_nominal_load")
    void measure_response_time_for_internal_case_management_calculation_decision_under_nominal_load() throws Exception {
        AtomicLong totalResponseTimeNanos = new AtomicLong(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(NOMINAL_LOAD_ITERATIONS);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);

        for (int i = 0; i < NOMINAL_LOAD_ITERATIONS; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long startTime = System.nanoTime();
                    decisionService.calculateDecision();
                    long endTime = System.nanoTime();
                    totalResponseTimeNanos.addAndGet(endTime - startTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long avgResponseTimeNanos = totalResponseTimeNanos.get() / NOMINAL_LOAD_ITERATIONS;
        long avgResponseTimeMs = TimeUnit.NANOSECONDS.toMillis(avgResponseTimeNanos);

        double throughput = (NOMINAL_LOAD_ITERATIONS * 1000.0) / avgResponseTimeMs;

        System.out.printf("Feature: Internal Case Management:calculation:decision%n");
        System.out.printf("Avg Response Time: %d ms%n", avgResponseTimeMs);
        System.out.printf("Throughput: %.2f ops/sec%n", throughput);
        System.out.printf("Concurrency: %d threads%n", CONCURRENT_THREADS);

        Assertions.assertTrue(avgResponseTimeMs <= MAX_RESPONSE_TIME_MS,
                "Average response time " + avgResponseTimeMs + "ms exceeded nominal threshold of " + MAX_RESPONSE_TIME_MS + "ms");
    }
}
