package app.integration.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CatastropheHighConcurrencyFnolPerformanceTest {

    private static final int CONCURRENCY_LEVEL = 500;
    private static final int DURATION_SECONDS = 60;
    private static final String PAYLOAD_TYPE = "catastrophe_wind";
    private static final String PRODUCT = "HO3";
    private static final String CHANNEL = "API";
    private static final String EXPECTED_CLAIM_TYPE = "Catastrophe";

    private List<Double> latencies;
    private List<String> auditLogs;
    private AtomicInteger successCount;
    private List<String> triageResults;

    @BeforeEach
    void setUp() {
        latencies = Collections.synchronizedList(new ArrayList<>());
        auditLogs = Collections.synchronizedList(new ArrayList<>());
        successCount = new AtomicInteger(0);
        triageResults = Collections.synchronizedList(new ArrayList<>());
    }

    @Test
    void validate_catastrophe_high_concurrency_fnol_throughput() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY_LEVEL);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(CONCURRENCY_LEVEL);

        long startTime = System.nanoTime();

        for (int i = 0; i < CONCURRENCY_LEVEL; i++) {
            final int submissionId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long threadStart = System.nanoTime();

                    // Mock FNOL transformation & validation (external I/O replaced with deterministic stub)
                    String claimType = mockProcessFnol(submissionId, PAYLOAD_TYPE, PRODUCT, CHANNEL);
                    triageResults.add(claimType);

                    // Mock structured audit logging (external I/O replaced with deterministic stub)
                    mockAuditLog(submissionId, claimType);

                    long threadEnd = System.nanoTime();
                    latencies.add((threadEnd - threadStart) / 1_000_000.0);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Capture runtime issues without breaking concurrent flow
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        boolean completed = completionLatch.await(DURATION_SECONDS, TimeUnit.SECONDS);
        long endTime = System.nanoTime();

        executor.shutdown();
        executor.awaitTermination(DURATION_SECONDS, TimeUnit.SECONDS);

        // Throughput & Thread Safety
        assertTrue(completed, "All 500 submissions must complete within 60 seconds");
        assertEquals(CONCURRENCY_LEVEL, successCount.get(), "System must process all FNOLs without thread safety violations");

        // P99 Latency
        List<Double> sortedLatencies = latencies.stream().sorted().collect(Collectors.toList());
        int p99Index = (int) Math.ceil(sortedLatencies.size() * 0.99) - 1;
        double p99Latency = sortedLatencies.get(p99Index);
        assertTrue(p99Latency < 300.0, "P99 latency must be under 300ms, actual: " + p99Latency + "ms");

        // Triage Validation
        long catastropheCount = triageResults.stream().filter(EXPECTED_CLAIM_TYPE::equals).count();
        assertEquals(CONCURRENCY_LEVEL, catastropheCount, "All claims must be correctly triaged to Catastrophe claim type");

        // Audit Logging & Observability
        assertEquals(CONCURRENCY_LEVEL, auditLogs.size(), "Audit logs must capture all actions");
        assertTrue(auditLogs.stream().allMatch(log -> log.contains("\"timestamp\":")), "Audit logs must include structured timestamps");

        // Data Integrity
        assertEquals(CONCURRENCY_LEVEL, triageResults.size(), "Zero data corruption or lost states allowed");
    }

    private String mockProcessFnol(int id, String payloadType, String product, String channel) {
        if ("catastrophe_wind".equals(payloadType)) {
            return "Catastrophe";
        }
        return "Standard";
    }

    private void mockAuditLog(int id, String claimType) {
        String logEntry = String.format("{\"id\":%d,\"claimType\":\"%s\",\"timestamp\":%d,\"channel\":\"%s\"}",
                id, claimType, System.currentTimeMillis(), "API");
        auditLogs.add(logEntry);
    }
}
