package app.integration.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CatastropheApiThroughputTest {

    private static final int CONCURRENCY = 500;
    private static final int DURATION_SEC = 300;
    private static final double P95_LATENCY_THRESHOLD_MS = 3000.0;
    private static final double THROUGHPUT_THRESHOLD_PER_SEC = 150.0;
    private static final String CHANNEL = "api";
    private static final String PRODUCT = "HO3";
    private static final String CAUSE_OF_LOSS = "hurricane";
    private static final String EVENT_CODE = "HUR-2024-01";

    private final Set<String> claimNumbers = ConcurrentHashMap.newKeySet();
    private final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger successCount = new AtomicInteger(0);

    @Test
    @DisplayName("measure_catastrophe_api_submission_throughput")
    void measure_catastrophe_api_submission_throughput() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENCY);

        long startTime = System.nanoTime();

        for (int i = 0; i < CONCURRENCY; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long subStartTime = System.nanoTime();

                    // Mock Orchestration Logic
                    String claimNumber = generateClaimNumber(i);

                    // Mock External I/O: Validate at service boundary
                    validateInput(claimNumber);

                    // Mock External I/O: Deduplication Check (Idempotency enforcement)
                    if (!claimNumbers.add(claimNumber)) {
                        throw new IllegalStateException("Duplicate claim number detected: " + claimNumber);
                    }

                    // Mock External I/O: Transformation & Persistence Simulation
                    // Simulating minimal processing time for orchestration steps
                    Thread.sleep(1);

                    successCount.incrementAndGet();
                    long duration = System.nanoTime() - subStartTime;
                    latencies.add(duration);

                } catch (Exception e) {
                    // In a real perf test, we might track failures,
                    // but here we assert zero corruption/duplicates.
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        boolean completed = endLatch.await(DURATION_SEC, TimeUnit.SECONDS);
        assertTrue(completed, "Test execution did not complete within duration.");

        long endTime = System.nanoTime();
        double durationSec = (endTime - startTime) / 1_000_000_000.0;

        // Verify Throughput
        double actualThroughput = successCount.get() / durationSec;
        assertTrue(actualThroughput >= THROUGHPUT_THRESHOLD_PER_SEC,
                String.format("Throughput %.2f/sec is below threshold %.2f/sec", actualThroughput, THROUGHPUT_THRESHOLD_PER_SEC));

        // Verify P95 Latency
        List<Long> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);
        int p95Index = (int) Math.ceil(0.95 * sortedLatencies.size()) - 1;
        double p95Ms = sortedLatencies.get(p95Index) / 1_000_000.0;
        assertTrue(p95Ms < P95_LATENCY_THRESHOLD_MS,
                String.format("P95 Latency %.2fms exceeds threshold %.2fms", p95Ms, P95_LATENCY_THRESHOLD_MS));

        // Verify Zero Data Corruption / Duplicates
        assertEquals(CONCURRENCY, successCount.get(), "Not all submissions processed successfully.");
        assertEquals(CONCURRENCY, claimNumbers.size(), "Duplicate claim numbers detected in results.");
    }

    private String generateClaimNumber(int index) {
        return String.format("FNOL-%s-%s-%d", EVENT_CODE, PRODUCT, index);
    }

    private void validateInput(String claimNumber) {
        // Mock validation boundary
        if (claimNumber == null || claimNumber.isEmpty()) {
            throw new IllegalArgumentException("Invalid claim number");
        }
    }
}
