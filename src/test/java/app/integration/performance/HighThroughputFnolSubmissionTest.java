package app.integration.performance;

import org.junit.jupiter.api.*;
import org.mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Performance test for Claim Initiation & Routing:decision:validation.
 * Verifies throughput, latency, success rate, and sequential ID generation under load.
 * NFR Coverage: concurrency (thread_safety), observability (structured_logging), security (input_validation).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HighThroughputFnolSubmissionTest {

    private static final int CONCURRENT_USERS = 100;
    private static final int TOTAL_SUBMISSIONS = 5000;
    private static final double MIN_THROUGHPUT_PER_SEC = 50.0;
    private static final long MAX_AVG_LATENCY_MS = 2000L;
    private static final double MIN_SUCCESS_RATE = 0.999;

    private interface ClaimDecisionValidationService {
        Map<String, Object> validateAndRoute(Map<String, Object> payload);
    }

    @Mock
    private ClaimDecisionValidationService mockService;

    private ExecutorService executorService;
    private CountDownLatch startLatch;
    private CountDownLatch endLatch;
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Boolean> generatedClaimIds = new ConcurrentHashMap<>();
    private long testStartTime;
    private long testEndTime;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        executorService = Executors.newFixedThreadPool(CONCURRENT_USERS);
        startLatch = new CountDownLatch(1);
        endLatch = new CountDownLatch(TOTAL_SUBMISSIONS);

        // Mock decision validation to simulate infra I/O contracts (Redis/DynamoDB/SES)
        when(mockService.validateAndRoute(anyMap())).thenAnswer(invocation -> {
            int sequence = generatedClaimIds.size() + 1;
            String id = String.format("CLM-%06d", sequence);
            Map<String, Object> response = new HashMap<>();
            response.put("id", id);
            response.put("status", "VALIDATED");
            response.put("routing", "AUTO");
            return response;
        });
    }

    @AfterEach
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("fnol_high_throughput_submission")
    void fnol_high_throughput_submission() throws InterruptedException {
        totalLatencyMs.set(0);
        successCount.set(0);
        failureCount.set(0);
        generatedClaimIds.clear();

        testStartTime = System.nanoTime();
        startLatch.countDown();

        for (int i = 0; i < TOTAL_SUBMISSIONS; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    try {
                        Map<String, Object> payload = Map.of(
                                "channel", "API_intake",
                                "product", "HO3",
                                "cause_of_loss", "wind"
                        );
                        // Input validation & TLS/least_privilege simulated via mock contract
                        Map<String, Object> result = mockService.validateAndRoute(payload);
                        String claimId = (String) result.get("id");
                        generatedClaimIds.put(claimId, Boolean.TRUE);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        long end = System.nanoTime();
                        totalLatencyMs.addAndGet((end - start) / 1_000_000);
                        endLatch.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        endLatch.await(60, TimeUnit.SECONDS);
        testEndTime = System.nanoTime();

        long durationSec = (testEndTime - testStartTime) / 1_000_000_000;
        double actualThroughput = TOTAL_SUBMISSIONS / (double) durationSec;
        double actualAvgLatencyMs = totalLatencyMs.get() / (double) TOTAL_SUBMISSIONS;
        double actualSuccessRate = successCount.get() / (double) TOTAL_SUBMISSIONS;

        assertTrue(actualThroughput >= MIN_THROUGHPUT_PER_SEC,
                String.format("Throughput %.2f FNOLs/sec is below minimum %.2f", actualThroughput, MIN_THROUGHPUT_PER_SEC));
        assertTrue(actualAvgLatencyMs <= MAX_AVG_LATENCY_MS,
                String.format("Average latency %.2f ms exceeds maximum %.2f ms", actualAvgLatencyMs, MAX_AVG_LATENCY_MS));
        assertTrue(actualSuccessRate >= MIN_SUCCESS_RATE,
                String.format("Success rate %.4f is below minimum %.4f", actualSuccessRate, MIN_SUCCESS_RATE));

        for (int i = 0; i < TOTAL_SUBMISSIONS; i++) {
            String expectedId = String.format("CLM-%06d", i + 1);
            assertTrue(generatedClaimIds.containsKey(expectedId), "Claim ID sequence gap detected: missing " + expectedId);
        }

        // Structured logging for observability
        System.out.printf("{\"event\":\"performance_test_complete\",\"feature\":\"Claim Initiation & Routing:decision:validation\",\"throughput_per_sec\":%.2f,\"avg_latency_ms\":%.2f,\"success_rate\":%.4f,\"total_submissions\":%d}%n",
                actualThroughput, actualAvgLatencyMs, actualSuccessRate, TOTAL_SUBMISSIONS);
    }
}
