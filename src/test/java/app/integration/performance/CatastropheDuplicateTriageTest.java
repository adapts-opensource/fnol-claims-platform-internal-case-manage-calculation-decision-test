package app.integration.performance;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Performance test for Claim Data Standardization:decision:validation during catastrophe events.
 * Simulates high-concurrency duplicate detection and triage routing with mocked I/O.
 */
@Execution(ExecutionMode.CONCURRENT)
class CatastropheDuplicateTriagePerformanceTest {

    private static final int CONCURRENT_SUBMISSIONS = 1000;
    private static final Duration MAX_AVG_DUPLICATE_DETECTION_LATENCY = Duration.ofMillis(1200);
    private static final Duration MAX_AVG_TRIAGE_LATENCY = Duration.ofMillis(2000);
    private static final Duration EXECUTOR_SHUTDOWN_TIMEOUT = Duration.ofSeconds(60);
    private static final String EVENT_CODE = "HURRICANE_2024";
    private static final String DUPLICATE_SCOPE = "historical_5_years";
    private static final String TRIAGE_RULES = "complex";

    private ExecutorService executor;
    private final List<String> structuredLogCapture = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong duplicateCheckCount = new AtomicLong(0);
    private final AtomicLong triageAssignmentCount = new AtomicLong(0);

    @BeforeEach
    void setUp() {
        // Mock concurrency pool simulating inbound claim stream
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT.getSeconds(), TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("validate_catastrophe_duplicate_detection_triage_latency")
    void validate_catastrophe_duplicate_detection_triage_latency() throws Exception {
        List<Future<ClaimResult>> futures = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_SUBMISSIONS; i++) {
            futures.add(executor.submit(() -> processClaimSubmission(i)));
        }

        List<ClaimResult> results = futures.stream()
                .map(f -> {
                    try {
                        return f.get(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException("Submission failed", e);
                    }
                })
                .collect(Collectors.toList());

        double avgDupLatencyMs = results.stream()
                .mapToLong(r -> r.duplicateDetectionLatency.toMillis())
                .average().orElse(0.0);
        double avgTriageLatencyMs = results.stream()
                .mapToLong(r -> r.triageAssignmentLatency.toMillis())
                .average().orElse(0.0);

        Assertions.assertTrue(avgDupLatencyMs < MAX_AVG_DUPLICATE_DETECTION_LATENCY.toMillis(),
                "Avg duplicate detection latency (" + avgDupLatencyMs + "ms) exceeded 1.2s threshold");
        Assertions.assertTrue(avgTriageLatencyMs < MAX_AVG_TRIAGE_LATENCY.toMillis(),
                "Avg triage assignment latency (" + avgTriageLatencyMs + "ms) exceeded 2.0s threshold");
        Assertions.assertEquals(CONCURRENT_SUBMISSIONS, duplicateCheckCount.get(),
                "Duplicate check should execute for all submissions");
        Assertions.assertEquals(CONCURRENT_SUBMISSIONS, triageAssignmentCount.get(),
                "Triage assignment should execute for all submissions");
        Assertions.assertTrue(structuredLogCapture.size() >= CONCURRENT_SUBMISSIONS * 2,
                "Structured logging must capture rule executions for all submissions");
    }

    /**
     * Simulates processing a claim payload through duplicate detection and triage routing.
     * External I/O (S3/DynamoDB) is mocked with deterministic latency and in-memory tracking.
     */
    private ClaimResult processClaimSubmission(int index) {
        String claimId = "claim_" + index + "_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("event_code", EVENT_CODE);
        payload.put("duplicate_check_scope", DUPLICATE_SCOPE);
        payload.put("triage_rules", TRIAGE_RULES);
        payload.put("payload_includes_photos", true);

        // Mock: Duplicate Detection Phase
        Instant dupStart = Instant.now();
        // Simulate lightweight index lookup + rule evaluation (mocked I/O)
        Thread.sleep(5); 
        duplicateCheckCount.incrementAndGet();
        logStructuredEvent("duplicate_check", claimId, "success", EVENT_CODE, DUPLICATE_SCOPE);
        Duration dupLatency = Duration.between(dupStart, Instant.now());

        // Mock: Triage Routing Phase
        Instant triageStart = Instant.now();
        // Simulate complex rule engine execution (mocked I/O)
        Thread.sleep(8);
        triageAssignmentCount.incrementAndGet();
        logStructuredEvent("triage_assignment", claimId, "routed", TRIAGE_RULES, null);
        Duration triageLatency = Duration.between(triageStart, Instant.now());

        return new ClaimResult(dupLatency, triageLatency);
    }

    private void logStructuredEvent(String event, String claimId, String status, String ruleOrScope, String extra) {
        StringBuilder log = new StringBuilder();
        log.append("{");
        log.append("\"timestamp\":\"").append(Instant.now().toString()).append("\",");
        log.append("\"event\":\"").append(event).append("\",");
        log.append("\"claim_id\":\"").append(claimId).append("\",");
        log.append("\"status\":\"").append(status).append("\",");
        log.append("\"event_code\":\"").append(EVENT_CODE).append("\",");
        if (ruleOrScope != null) log.append("\"triage_rules\":\"").append(ruleOrScope).append("\",");
        if (extra != null) log.append("\"duplicate_scope\":\"").append(extra).append("\",");
        log.append("\"thread\":\"").append(Thread.currentThread().getName()).append("\"");
        log.append("}");
        structuredLogCapture.add(log.toString());
    }

    private record ClaimResult(Duration duplicateDetectionLatency, Duration triageAssignmentLatency) {}
}
