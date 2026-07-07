package app.integration.performance;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

public class ClaimDataStandardizationEnrichmentValidationPerformanceTest {

    private final ClaimValidationService validationService = new MockClaimValidationService();
    private static final int REQUEST_COUNT = 200;
    private static final long MAX_P95_LATENCY_MS = 100;
    private static final long MAX_TOTAL_TEST_DURATION_MS = 5000;

    @Test
    void verify_concurrent_requests_for_claim_data_standardization_enrichment_validation_stay_within_latency_bounds() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(REQUEST_COUNT);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        AtomicLong totalStartTime = new AtomicLong(System.nanoTime());

        for (int i = 0; i < REQUEST_COUNT; i++) {
            final int requestId = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    validationService.validateClaim("claim-" + requestId, Map.of("type", "FNOL", "amount", 1000));
                    long end = System.nanoTime();
                    latencies.add((end - start) / 1_000_000);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);

        long totalDurationMs = (System.nanoTime() - totalStartTime.get()) / 1_000_000;
        assertTrue(errors.isEmpty(), "No exceptions should occur during concurrent validation");
        assertTrue(latencies.size() == REQUEST_COUNT, "All requests should complete");
        assertTrue(totalDurationMs <= MAX_TOTAL_TEST_DURATION_MS, "Total duration should be within bounds: " + totalDurationMs + "ms");

        List<Long> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);
        int p95Index = (int) Math.ceil(0.95 * sortedLatencies.size()) - 1;
        long p95Latency = sortedLatencies.get(p95Index);
        assertTrue(p95Latency <= MAX_P95_LATENCY_MS, "P95 latency should be within bounds: " + p95Latency + "ms");
    }

    private interface ClaimValidationService {
        Map<String, Object> validateClaim(String id, Map<String, Object> payload);
    }

    private static class MockClaimValidationService implements ClaimValidationService {
        @Override
        public Map<String, Object> validateClaim(String id, Map<String, Object> payload) {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Map<String, Object> result = new HashMap<>();
            result.put("id", id);
            result.put("status", "validated");
            result.put("enriched", true);
            return result;
        }
    }
}
