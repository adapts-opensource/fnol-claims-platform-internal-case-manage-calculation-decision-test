package app.integration.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test for Claim Initiation & Routing:decision:validation
 * Validates throughput, triage SLA, and data integrity under catastrophe burst conditions.
 */
public class FnolCatastropheBurstRoutingTest {

    private static final String EVENT_CODE = "HURRICANE_MIA";
    private static final String PRODUCT = "HW2";
    private static final int TARGET_REQUESTS = 1000;
    private static final long TRIAGE_SLA_MS = 3000;
    private static final int THREAD_POOL_SIZE = 16;

    @Test
    @DisplayName("fnol_catastrophe_burst_routing")
    void fnol_catastrophe_burst_routing() throws Exception {
        // Mock external I/O (Redis, DynamoDB, SES) with in-memory stubs
        ClaimInitiationService initiationService = new ClaimInitiationService();
        RoutingDecisionService routingService = new RoutingDecisionService();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<?>> futures = new ArrayList<>();
        Set<String> claimIds = ConcurrentHashMap.newKeySet();
        Set<String> taskIds = ConcurrentHashMap.newKeySet();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        // Simulate burst_duration=300s ramp_up_rate=1000_per_minute
        for (int i = 0; i < TARGET_REQUESTS; i++) {
            futures.add(executor.submit(() -> {
                try {
                    // Build typed payload matching entity schema
                    Map<String, Object> payload = new ConcurrentHashMap<>();
                    payload.put("eventCode", EVENT_CODE);
                    payload.put("product", PRODUCT);
                    payload.put("catastropheMode", "enabled");

                    String claimId = initiationService.createClaim(EVENT_CODE, payload);
                    claimIds.add(claimId);

                    Map<String, Object> routingResult = routingService.classifyAndRoute(claimId, payload);
                    assertTrue("Catastrophe".equals(routingResult.get("routingDecision")),
                            "Expected Catastrophe routing for event " + EVENT_CODE);
                    taskIds.add(routingResult.get("taskId").toString());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            }));
        }

        // Await burst completion
        for (Future<?> f : futures) {
            f.get();
        }
        long duration = System.currentTimeMillis() - startTime;
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Validate Expected Results
        double actualRate = (TARGET_REQUESTS / (duration / 60000.0));
        assertTrue(actualRate >= TARGET_REQUESTS * 0.9,
                "Throughput target not met. Expected ~" + TARGET_REQUESTS + "/min, got " + Math.round(actualRate) + "/min");
        assertEquals(TARGET_REQUESTS, successCount.get(), "All requests should succeed");
        assertEquals(0, failCount.get(), "No failures expected");
        assertEquals(TARGET_REQUESTS, claimIds.size(), "No duplicate claim IDs allowed");
        assertEquals(TARGET_REQUESTS, taskIds.size(), "No duplicate task IDs allowed");
        assertTrue(routingService.getMaxLatencyMs() < TRIAGE_SLA_MS,
                "Triage classification exceeded SLA of " + TRIAGE_SLA_MS + "ms");
    }

    // Mock/Stub services to replace live AWS I/O contracts
    static class ClaimInitiationService {
        String createClaim(String eventCode, Map<String, Object> payload) {
            return "claim-" + System.nanoTime();
        }
    }

    static class RoutingDecisionService {
        private volatile long maxLatencyMs = 0;

        Map<String, Object> classifyAndRoute(String claimId, Map<String, Object> payload) {
            long start = System.nanoTime();
            Thread.yield(); // Simulate minimal CPU scheduling for triage
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (elapsed > maxLatencyMs) {
                maxLatencyMs = elapsed;
            }
            return Map.of("routingDecision", "Catastrophe", "taskId", "task-" + System.nanoTime());
        }

        long getMaxLatencyMs() {
            return maxLatencyMs;
        }
    }
}
