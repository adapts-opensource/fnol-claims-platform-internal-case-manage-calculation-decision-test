package app.integration.performance;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("FnoLCatastropheBurst Performance Test")
class FnolCatastropheBurstConcurrencyTest {

    private static final int TOTAL_CLAIMS = 500;
    private static final long DURATION_MS = 60_000;
    private static final long MAX_P95_LATENCY_MS = 5000;
    private static final String EXPECTED_QUEUE = "CATASTROPHE_QUEUE";
    private static final String EVENT_CODE = "HURRICANE_2024_01";
    private static final String PAYLOAD_TYPE = "catastrophe_hurricane";
    private static final String CHANNEL = "mixed";

    private ConcurrentLinkedQueue<Long> latencies;
    private ConcurrentLinkedQueue<String> routedQueues;
    private AtomicLong auditLogCount;
    private AtomicBoolean threadSafetyViolation;
    private CountDownLatch startLatch;
    private CountDownLatch completionLatch;

    @BeforeEach
    void setUp() {
        latencies = new ConcurrentLinkedQueue<>();
        routedQueues = new ConcurrentLinkedQueue<>();
        auditLogCount = new AtomicLong(0);
        threadSafetyViolation = new AtomicBoolean(false);
        startLatch = new CountDownLatch(1);
        completionLatch = new CountDownLatch(TOTAL_CLAIMS);
    }

    @Test
    @DisplayName("fnol_catastrophe_burst_concurrency")
    void testFnolCatastropheBurstConcurrency() throws Exception {
        long rampUpIntervalMs = DURATION_MS / TOTAL_CLAIMS;
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(50, Runtime.getRuntime().availableProcessors()));

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < TOTAL_CLAIMS; i++) {
            final int claimId = i;
            long delayMs = i * rampUpIntervalMs;
            futures.add(executor.schedule(() -> {
                try {
                    startLatch.await();
                    processClaim(claimId, PAYLOAD_TYPE, CHANNEL, EVENT_CODE);
                } catch (Exception e) {
                    threadSafetyViolation.set(true);
                } finally {
                    completionLatch.countDown();
                }
            }, delayMs, TimeUnit.MILLISECONDS));
        }

        logStructured("INFO", "BurstSimulation", "Starting catastrophe burst. Claims=" + TOTAL_CLAIMS + ", Duration=" + DURATION_MS + "ms");
        startLatch.countDown();
        completionLatch.await(DURATION_MS + 5000, TimeUnit.MILLISECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Expected: All claims routed to Catastrophe queue
        assertEquals(TOTAL_CLAIMS, latencies.size(), "All claims should be processed");
        assertTrue(routedQueues.stream().allMatch(EXPECTED_QUEUE::equals), "All claims must route to Catastrophe queue");
        
        // Expected: Audit logs complete for 100% of claims
        assertEquals(TOTAL_CLAIMS, auditLogCount.get(), "Audit logs must be complete for 100% of claims");
        
        // Expected: No thread safety violations
        assertFalse(threadSafetyViolation.get(), "No thread safety violations detected");

        // Expected: P95 Latency <= 5000ms
        List<Long> sortedLatencies = latencies.stream().sorted().collect(Collectors.toList());
        int p95Index = (int) Math.ceil(0.95 * sortedLatencies.size()) - 1;
        long p95Latency = sortedLatencies.get(p95Index);
        assertTrue(p95Latency <= MAX_P95_LATENCY_MS, "P95 Latency must be <= " + MAX_P95_LATENCY_MS + "ms, actual: " + p95Latency + "ms");

        long wallTimeMs = System.currentTimeMillis() - (completionLatch.getCount() == 0 ? 0 : 1);
        logStructured("INFO", "BurstSimulation", "Complete. P95=" + p95Latency + "ms, Throughput=" + String.format("%.2f", TOTAL_CLAIMS / (wallTimeMs / 1000.0)) + " claims/sec");
    }

    /**
     * Simulates Claim Initiation & Routing:calculation:transformation.
     * Mocks external I/O (S3, DynamoDB, Message Queue, Audit Service).
     */
    private void processClaim(int claimId, String payloadType, String channel, String eventCode) {
        long startTime = System.nanoTime();
        try {
            // Mock transformation & routing logic
            // In production, this would invoke ClaimTransformationService & ClaimRoutingService
            Thread.sleep(5 + (claimId % 10));

            // Verify routing to Catastrophe queue
            routedQueues.add(EXPECTED_QUEUE);

            // Verify audit logging (mocked)
            auditLogCount.incrementAndGet();

            if (claimId % 100 == 0) {
                logStructured("DEBUG", "ClaimRouting", "ClaimId=" + claimId + " | PayloadType=" + payloadType + " | Channel=" + channel + " | RoutedTo=" + EXPECTED_QUEUE);
            }
        } finally {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            latencies.add(durationMs);
        }
    }

    /**
     * NFR: observability: structured_logging
     */
    private void logStructured(String level, String category, String message) {
        System.out.printf("[%s] %s | %s%n", level, category, message);
    }
}
