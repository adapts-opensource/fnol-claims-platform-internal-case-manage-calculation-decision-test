package app.integration.performance;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@DisplayName("FnoL Standard Throughput Performance Test")
class FnoLStandardThroughput {

    // Test inputs mapped to constants
    private static final String PAYLOAD_TYPE = "standard_ho3";
    private static final String CHANNEL = "api";
    private static final int TARGET_TPS = 50;
    private static final long DURATION_SEC = 300;
    private static final long MAX_P99_LATENCY_MS = 2000;

    private MockTransformationService mockService;

    @BeforeEach
    void setUp() {
        mockService = new MockTransformationService();
    }

    @Test
    @DisplayName("fnol_standard_throughput_baseline")
    void fnol_standard_throughput_baseline() throws InterruptedException {
        int threadCount = TARGET_TPS;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // Thread-safe collections for concurrency-safe metric aggregation
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // To achieve TARGET_TPS over DURATION_SEC with threadCount threads,
        // each thread sends 1 request per second to maintain steady load.
        int requestsPerThread = DURATION_SEC;

        long startTime = System.nanoTime();
        startLatch.countDown(); // Signal all threads to start simultaneously

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int r = 0; r < requestsPerThread; r++) {
                        long reqStart = System.nanoTime();
                        mockService.transformClaimData(PAYLOAD_TYPE, CHANNEL);
                        long reqEnd = System.nanoTime();
                        latencies.add((reqEnd - reqStart) / 1_000_000); // Convert to ms
                        successCount.incrementAndGet();
                        if (r < requestsPerThread - 1) {
                            Thread.sleep(1000); // Steady load pacing
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await();
        long endTime = System.nanoTime();
        double actualDurationSec = (endTime - startTime) / 1_000_000_000.0;

        double actualTps = successCount.get() / actualDurationSec;
        long p99Latency = calculateP99(latencies);
        double errorRate = (double) errorCount.get() / (successCount.get() + errorCount.get());

        // Assertions matching expected results
        Assertions.assertTrue(actualTps >= TARGET_TPS,
                "Expected TPS >= " + TARGET_TPS + ", but got " + String.format("%.2f", actualTps));
        Assertions.assertTrue(p99Latency <= MAX_P99_LATENCY_MS,
                "Expected P99 Latency <= " + MAX_P99_LATENCY_MS + "ms, but got " + p99Latency + "ms");
        Assertions.assertEquals(0.0, errorRate, 0.001,
                "Expected Error Rate = 0%, but got " + String.format("%.2f", errorRate * 100) + "%");
        Assertions.assertTrue(mockService.isAllAuditLogsCaptured(successCount.get()),
                "Expected audit logs captured for all requests.");

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    private long calculateP99(List<Long> latencies) {
        if (latencies.isEmpty()) return 0;
        Collections.sort(latencies);
        int index = (int) Math.ceil(0.99 * latencies.size()) - 1;
        return latencies.get(index);
    }

    // Mock service simulating the transformation logic without external I/O
    static class MockTransformationService {
        private final AtomicInteger callCount = new AtomicInteger(0);
        private final List<String> auditLogs = Collections.synchronizedList(new ArrayList<>());

        void transformClaimData(String payloadType, String channel) {
            // Simulate lightweight transformation processing
            try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            callCount.incrementAndGet();
            // Structured logging simulation for observability NFR
            auditLogs.add(String.format("{\"event\":\"transformation\",\"payloadType\":\"%s\",\"channel\":\"%s\",\"timestamp\":%d}",
                    payloadType, channel, System.currentTimeMillis()));
        }

        boolean isAllAuditLogsCaptured(int expectedCalls) {
            return callCount.get() == expectedCalls && auditLogs.size() == expectedCalls;
        }
    }
}
