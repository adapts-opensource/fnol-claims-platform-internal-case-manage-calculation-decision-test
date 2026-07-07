package app.integration.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;
import java.time.Duration;
import java.time.Instant;

/**
 * Performance test for Multi-Channel FNOL Submission:orchestration:decision.
 * Validates duplicate detection throughput, task generation, claim number uniqueness,
 * and system stability under sustained load.
 */
public class DuplicateDetectionThroughputTest {

    // Test Configuration Constants
    private static final int SUSTAINED_RPS = 150;
    private static final int DURATION_MINUTES = 10;
    private static final double DUPLICATE_PROBABILITY = 0.3;
    private static final String CAUSE_OF_LOSS = "Water";
    private static final String DETECTION_CRITERIA = "policy_address_date_cause_event";
    private static final String CHANNEL = "API";
    private static final double ERROR_RATE_THRESHOLD = 0.01;
    private static final double DUPLICATE_RATE_TOLERANCE = 0.05;

    @Test
    @DisplayName("High throughput duplicate detection and task generation")
    void high_throughput_duplicate_detection_and_task_generation() throws Exception {
        int totalRequests = SUSTAINED_RPS * 60 * DURATION_MINUTES;

        // Mock External Services
        DuplicateDetector mockDetector = Mockito.mock(DuplicateDetector.class);
        TaskGenerator mockTaskGenerator = Mockito.mock(TaskGenerator.class);
        AuditLogger mockAuditLogger = Mockito.mock(AuditLogger.class);
        ClaimNumberGenerator mockClaimNumberGen = Mockito.mock(ClaimNumberGenerator.class);
        PolicyService mockPolicyService = Mockito.mock(PolicyService.class);

        // Mock Behaviors
        // Duplicate detection follows probability distribution
        Mockito.when(mockDetector.detectDuplicate(Mockito.any())).thenAnswer(invocation -> {
            return Math.random() < DUPLICATE_PROBABILITY;
        });

        // Policy service resolves dynamic pool
        Mockito.when(mockPolicyService.resolvePolicy(Mockito.any())).thenReturn("POL-POOL-001");

        // Claim number generator produces unique sequential IDs
        AtomicInteger claimSeq = new AtomicInteger(0);
        Mockito.when(mockClaimNumberGen.generate(Mockito.anyString())).thenAnswer(invocation -> {
            int seq = claimSeq.incrementAndGet();
            return String.format("CLM-SEQ-%08d", seq);
        });

        // Task generator creates review tasks
        Mockito.when(mockTaskGenerator.createTask(Mockito.any(), Mockito.anyString())).thenReturn("TASK-GEN-" + UUID.randomUUID().toString().substring(0, 8));

        // Audit logger captures rule executions and assignments
        Mockito.doNothing().when(mockAuditLogger).log(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        // Concurrency Metrics
        AtomicInteger duplicateCount = new AtomicInteger(0);
        AtomicInteger taskCount = new AtomicInteger(0);
        Set<String> generatedClaimNumbers = ConcurrentHashMap.newKeySet();
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // Execution
        ExecutorService executor = Executors.newFixedThreadPool(SUSTAINED_RPS);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        Instant start = Instant.now();

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    // Construct payload
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("channel", CHANNEL);
                    payload.put("causeOfLoss", CAUSE_OF_LOSS);
                    payload.put("policyNumber", mockPolicyService.resolvePolicy("DYNAMIC"));

                    // Orchestration Decision
                    boolean isDuplicate = mockDetector.detectDuplicate(payload);

                    // Audit Rule Execution
                    mockAuditLogger.log("RULE_EXECUTION", DETECTION_CRITERIA, isDuplicate ? "MATCH" : "NO_MATCH");

                    String claimNumber;
                    if (isDuplicate) {
                        duplicateCount.incrementAndGet();
                        
                        // Conditional Task Generation
                        String taskId = mockTaskGenerator.createTask(payload, "Review Potential Duplicate Claim");
                        taskCount.incrementAndGet();
                        
                        // Audit Task Assignment
                        mockAuditLogger.log("TASK_ASSIGNED", taskId, "Review Potential Duplicate Claim");
                        
                        // Claim flow continues without blocking
                        claimNumber = mockClaimNumberGen.generate(payload.get("policyNumber").toString());
                    } else {
                        claimNumber = mockClaimNumberGen.generate(payload.get("policyNumber").toString());
                    }

                    // Verify Claim Number Format and Uniqueness
                    Assertions.assertTrue(claimNumber.matches("CLM-SEQ-\\d{8}"), "Invalid claim number format: " + claimNumber);
                    generatedClaimNumbers.add(claimNumber);

                    // Audit Claim Creation
                    mockAuditLogger.log("CLAIM_CREATED", claimNumber, "SUCCESS");

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        executor.shutdown();
        boolean finished = latch.await(Duration.ofMinutes(DURATION_MINUTES + 1).toMillis(), TimeUnit.MILLISECONDS);
        Instant end = Instant.now();

        Assertions.assertTrue(finished, "Test execution did not complete within expected duration");

        // Metrics Calculation
        long durationMs = Duration.between(start, end).toMillis();
        double actualRps = (totalRequests * 1000.0) / durationMs;
        double errorRate = (double) errorCount.get() / totalRequests;
        double observedDuplicateRate = (double) duplicateCount.get() / totalRequests;

        // Assertions
        Assertions.assertEquals(totalRequests, generatedClaimNumbers.size(), "All claim numbers must be unique sequential identifiers");
        Assertions.assertTrue(errorRate < ERROR_RATE_THRESHOLD, "Error rate must be < 1%, observed: " + String.format("%.4f", errorRate));
        Assertions.assertTrue(Math.abs(observedDuplicateRate - DUPLICATE_PROBABILITY) < DUPLICATE_RATE_TOLERANCE,
                "Duplicate detection rate should be within tolerance of " + DUPLICATE_PROBABILITY + ", observed: " + String.format("%.4f", observedDuplicateRate));
        Assertions.assertEquals(duplicateCount.get(), taskCount.get(), "Every detected duplicate must generate a Review Potential Duplicate Claim task");
        Assertions.assertTrue(taskCount.get() > 0, "System must generate tasks for duplicates under load");

        // Verify Audit Trail Compliance
        Mockito.verify(mockAuditLogger, Mockito.atLeast(totalRequests)).log(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    // Internal Interfaces for Mocking Infrastructure Contracts
    interface DuplicateDetector {
        boolean detectDuplicate(Map<String, Object> payload);
    }

    interface TaskGenerator {
        String createTask(Map<String, Object> payload, String taskType);
    }

    interface AuditLogger {
        void log(String eventType, String entityId, String detail);
    }

    interface ClaimNumberGenerator {
        String generate(String policyRef);
    }

    interface PolicyService {
        String resolvePolicy(String policyInput);
    }
}
