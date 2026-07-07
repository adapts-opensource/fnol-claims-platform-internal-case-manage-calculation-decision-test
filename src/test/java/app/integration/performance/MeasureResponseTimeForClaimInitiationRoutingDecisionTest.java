package app.integration.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClaimInitiationRoutingDecisionCalculationPerformanceTest {
    private static final int NOMINAL_CONCURRENCY = 50;
    private static final int ITERATIONS_PER_THREAD = 10;
    private static final Duration MAX_P95_LATENCY = Duration.ofMillis(500);
    private static final Duration MAX_P99_LATENCY = Duration.ofMillis(800);

    private DecisionCalculationService mockService;

    @BeforeEach
    void setUp() {
        mockService = mock(DecisionCalculationService.class);
        when(mockService.calculateDecision(anyString(), anyMap())).thenReturn(Map.of("status", "APPROVED"));
    }

    @Test
    void measure_response_time_for_claim_initiation_routing_decision_calculation_under_nominal_load() throws Exception {
        List<Duration> latencies = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(NOMINAL_CONCURRENCY);

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < NOMINAL_CONCURRENCY; i++) {
                futures.add(CompletableFuture.runAsync(() -> {
                    long start = System.nanoTime();
                    try {
                        for (int j = 0; j < ITERATIONS_PER_THREAD; j++) {
                            mockService.calculateDecision("claim-id-" + i, Map.of("type", "FNOL"));
                            Thread.sleep(1);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    long end = System.nanoTime();
                    synchronized (latencies) {
                        latencies.add(Duration.ofNanos(end - start));
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }

        List<Duration> sortedLatencies = latencies.stream().sorted().collect(Collectors.toList());
        int p95Index = (int) Math.ceil(sortedLatencies.size() * 0.95) - 1;
        int p99Index = (int) Math.ceil(sortedLatencies.size() * 0.99) - 1;
        Duration p95 = sortedLatencies.get(p95Index);
        Duration p99 = sortedLatencies.get(p99Index);

        assertTrue(p95.compareTo(MAX_P95_LATENCY) <= 0,
                "P95 latency (" + p95 + ") exceeds threshold (" + MAX_P95_LATENCY + ")");
        assertTrue(p99.compareTo(MAX_P99_LATENCY) <= 0,
                "P99 latency (" + p99 + ") exceeds threshold (" + MAX_P99_LATENCY + ")");

        double throughput = (double) sortedLatencies.size() / p99.toSeconds();
        assertTrue(throughput > 0, "Throughput should be positive");
    }

    private interface DecisionCalculationService {
        Map<String, Object> calculateDecision(String id, Map<String, Object> payload);
    }
}
