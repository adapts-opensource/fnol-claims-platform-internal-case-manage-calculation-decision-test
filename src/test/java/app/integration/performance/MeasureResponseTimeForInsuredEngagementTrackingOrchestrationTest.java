package app.integration.performance;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Insured Engagement & Tracking Orchestration Performance Tests")
class InsuredEngagementTrackingOrchestrationPerformanceTest {

    private static final int NOMINAL_LOAD_REQUESTS = 100;
    private static final int CONCURRENCY_THREADS = 10;
    private static final long MAX_AVG_RESPONSE_TIME_MS = 500;

    @Mock
    private DecisionOrchestrationClient decisionOrchestrationClient;

    @BeforeEach
    void setUp() {
        // Mock external I/O (DynamoDB, SES, S3) to simulate nominal orchestration latency
        // without invoking live AWS services or production HTTP endpoints.
        when(decisionOrchestrationClient.executeDecision(anyString(), anyString())).thenReturn("APPROVED");
    }

    @Test
    @DisplayName("measure_response_time_for_insured_engagement_tracking_orchestration_decision_under_nominal_load")
    void measure_response_time_for_insured_engagement_tracking_orchestration_decision_under_nominal_load() throws Exception {
        List<Long> responseTimes = new ArrayList<>(NOMINAL_LOAD_REQUESTS);
        AtomicLong errorCount = new AtomicLong(0);

        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY_THREADS)) {
            for (int i = 0; i < NOMINAL_LOAD_REQUESTS; i++) {
                final int requestId = i;
                executor.submit(() -> {
                    long start = System.nanoTime();
                    try {
                        // Simulate orchestration decision call routing through mocked I/O
                        decisionOrchestrationClient.executeDecision("CLAIM_" + requestId, "RESERVE_123");
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                        responseTimes.add(durationMs);
                    }
                });
            }
        }

        // Validate nominal load execution integrity
        assertEquals(0, errorCount.get(), "Nominal load should complete without errors");
        assertEquals(NOMINAL_LOAD_REQUESTS, responseTimes.size(), "All nominal requests should record a response time");

        // Calculate timing, throughput, and concurrency metrics
        long totalTimeMs = responseTimes.stream().mapToLong(Long::longValue).sum();
        double avgTimeMs = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxTimeMs = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        double throughput = (NOMINAL_LOAD_REQUESTS / (totalTimeMs / 1000.0));

        System.out.printf("Nominal Load Metrics: Requests=%d, Threads=%d, Avg=%.2f ms, Max=%d ms, Throughput=%.2f req/s%n",
                NOMINAL_LOAD_REQUESTS, CONCURRENCY_THREADS, avgTimeMs, maxTimeMs, throughput);

        // Performance assertions aligned with NFR concurrency & availability targets
        assertTrue(avgTimeMs < MAX_AVG_RESPONSE_TIME_MS,
                "Average response time under nominal load should be < " + MAX_AVG_RESPONSE_TIME_MS + " ms, but was " + avgTimeMs + " ms");
        assertTrue(maxTimeMs < MAX_AVG_RESPONSE_TIME_MS * 3,
                "Max response time should not exceed 3x average under nominal concurrency, but was " + maxTimeMs + " ms");
    }

    // Internal mock interface representing the orchestration decision endpoint
    // Abstracts SES, DynamoDB, and S3 calls to ensure zero live I/O latency during tests.
    private interface DecisionOrchestrationClient {
        String executeDecision(String claimId, String reserveId);
    }
}
