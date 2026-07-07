package app.integration.performance;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Performance test for Claim Data Standardization:decision:validation.
 * Validates throughput, latency, and integrity under concurrent FNOL submissions.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrentFnolValidationPerformanceTest {

    private static final int CONCURRENT_USERS = 500;
    private static final int DURATION_SECONDS = 60;
    private static final int TARGET_THROUGHPUT_PER_SEC = 50;
    private static final int FNOL_PAYLOAD_SIZE_KB = 15;
    private static final String POLICY_MATCH_ALGORITHM = "exact";
    private static final double SUCCESS_RATE_THRESHOLD = 0.99;
    private static final long P95_LATENCY_MS_THRESHOLD = 800;

    @Mock
    private PolicyMatchService policyMatchService;
    @Mock
    private DateOfLossValidator dateOfLossValidator;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ClaimDataStore claimDataStore;

    private ClaimValidationService validationService;
    private Map<String, String> payloadIntegrityRegistry;

    @BeforeEach
    void setUp() {
        payloadIntegrityRegistry = new ConcurrentHashMap<>();

        // Mock external I/O contracts
        lenient().when(policyMatchService.match(any(String.class)))
                .thenAnswer(invocation -> {
                    String payload = invocation.getArgument(0);
                    // Simulate exact policy match algorithm
                    payloadIntegrityRegistry.put(payload, payload);
                    return true;
                });
        lenient().when(dateOfLossValidator.validate(any(String.class))).thenReturn(true);
        lenient().when(claimDataStore.save(any(String.class))).thenReturn("s3://bucket/key.json");
        lenient().doNothing().when(auditLogService).log(any(String.class), any(String.class));

        // Wire service
        validationService = new ClaimValidationService(
                policyMatchService,
                dateOfLossValidator,
                auditLogService,
                claimDataStore
        );
    }

    @Test
    void validate_concurrent_fnol_policy_match_throughput() throws InterruptedException {
        // Arrange
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(CONCURRENT_USERS);

        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failCount = new AtomicLong(0);
        List<Duration> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicLong auditLogCount = new AtomicLong(0);

        String samplePayload = generatePayload(FNOL_PAYLOAD_SIZE_KB);
        Instant testStart = Instant.now();

        // Act: Concurrent submissions over duration
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Instant deadline = Instant.now().plusSeconds(DURATION_SECONDS);
                    while (Instant.now().isBefore(deadline)) {
                        long reqStart = System.nanoTime();
                        try {
                            // Execute validation logic
                            Map<String, Object> result = validationService.validate(samplePayload);
                            long reqEnd = System.nanoTime();

                            successCount.incrementAndGet();
                            latencies.add(Duration.ofNanos(reqEnd - reqStart));
                            auditLogCount.incrementAndGet();

                        } catch (Exception e) {
                            failCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();

        // Wait for duration completion
        completionLatch.await(DURATION_SECONDS + 10, TimeUnit.SECONDS);
        Instant testEnd = Instant.now();

        // Assert
        long totalDurationMs = Duration.between(testStart, testEnd).toMillis();
        double actualThroughput = (double) successCount.get() / (totalDurationMs / 1000.0);
        double actualSuccessRate = (double) successCount.get() / (successCount.get() + failCount.get());
        long p95Latency = calculateP95Latency(latencies);

        // 1. Throughput meets target
        assertTrue(actualThroughput >= TARGET_THROUGHPUT_PER_SEC,
                String.format("Throughput %.2f req/s is below target %d req/s", actualThroughput, TARGET_THROUGHPUT_PER_SEC));

        // 2. P95 Latency under threshold
        assertTrue(p95Latency < P95_LATENCY_MS_THRESHOLD,
                String.format("P95 latency %d ms exceeds threshold %d ms", p95Latency, P95_LATENCY_MS_THRESHOLD));

        // 3. Success rate meets threshold
        assertTrue(actualSuccessRate >= SUCCESS_RATE_THRESHOLD,
                String.format("Success rate %.2f%% is below threshold %.0f%%", actualSuccessRate * 100, SUCCESS_RATE_THRESHOLD * 100));

        // 4. Zero data corruption: Payloads in registry match inputs
        assertEquals(successCount.get(), payloadIntegrityRegistry.size(),
                "Payload integrity check failed: mismatch between submissions and registry");
        for (String payload : payloadIntegrityRegistry.values()) {
            assertEquals(samplePayload, payload, "Data corruption detected in payload");
        }

        // 5. Audit logs generated for every submission
        verify(auditLogService, times((int) successCount.get())).log(any(), any());
        assertEquals(successCount.get(), auditLogCount.get(),
                "Audit log count mismatch with successful submissions");
    }

    private String generatePayload(int sizeKb) {
        StringBuilder sb = new StringBuilder();
        int charsPerKb = sizeKb * 1024;
        for (int i = 0; i < charsPerKb; i++) {
            sb.append("A");
        }
        return sb.toString();
    }

    private long calculateP95Latency(List<Duration> latencies) {
        if (latencies.isEmpty()) {
            return 0;
        }
        List<Duration> sorted = new ArrayList<>(latencies);
        sorted.sort(Duration::compareTo);
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return sorted.get(index).toMillis();
    }
}
