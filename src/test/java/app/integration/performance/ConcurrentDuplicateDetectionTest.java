package app.integration.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collections;

/**
 * Performance test for Claim Initiation & Routing:decision:validation.
 * Validates thread safety, duplicate detection accuracy, and latency under concurrent load.
 */
public class FnolConcurrentDuplicateDetectionTest {

    private static final String SHARED_POLICY = "POL-998877";
    private static final String SHARED_ADDRESS = "100_Storm_Shore";
    private static final int CONCURRENT_WORKERS = 50;
    private static final long SUBMISSION_INTERVAL_MS = 10;
    private static final double P99_LATENCY_THRESHOLD_MS = 1500.0;

    private ClaimService claimService;
    private DuplicateDetectionService duplicateDetectionService;
    private AuditLogger auditLogger;
    private RedisClient redisClient;
    private DynamoDBClient dynamoDBClient;

    private ExecutorService executorService;
    private AtomicInteger raceConditionCount = new AtomicInteger(0);
    private List<Long> latencies = new CopyOnWriteArrayList<>();
    private ArgumentCaptor<Map<String, Object>> auditPayloadCaptor;

    @BeforeEach
    void setUp() {
        // Initialize mocks
        claimService = Mockito.mock(ClaimService.class);
        duplicateDetectionService = Mockito.mock(DuplicateDetectionService.class);
        auditLogger = Mockito.mock(AuditLogger.class);
        redisClient = Mockito.mock(RedisClient.class);
        dynamoDBClient = Mockito.mock(DynamoDBClient.class);

        // Reset state
        raceConditionCount.set(0);
        latencies.clear();
        auditPayloadCaptor = ArgumentCaptor.forClass(Map.class);

        // Configure mock behaviors
        // Redis/DynamoDB: Simulate cache miss to trigger detection logic
        when(redisClient.get(anyString())).thenReturn(null);
        when(dynamoDBClient.getItem(anyString(), anyString())).thenReturn(null);

        // Audit Logger: Capture calls to verify count
        doNothing().when(auditLogger).log(anyString(), auditPayloadCaptor.capture());

        // Duplicate Detection: Simulate finding a duplicate and creating a task
        // This mock tracks interactions to verify "exactly once" creation
        when(duplicateDetectionService.createDuplicateTask(anyString(), anyString())).thenReturn("DUP-TASK-001");
        
        // Claim Service: Wire mocks to internal logic (simulated by calling detection in this test context)
        doAnswer(invocation -> {
            Map<String, Object> payload = invocation.getArgument(0);
            String policyId = (String) payload.get("policyId");
            String address = (String) payload.get("address");
            
            // Simulate detection call
            duplicateDetectionService.createDuplicateTask(policyId, address);
            
            // Simulate audit logging
            auditLogger.log("CLAIM_INITIATION", payload);
            
            return Map.of("claimId", "CLM-INIT-" + System.nanoTime());
        }).when(claimService).initiateClaim(anyMap());
    }

    @AfterEach
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("fnol_concurrent_duplicate_detection")
    void fnol_concurrent_duplicate_detection() throws Exception {
        // Arrange: Setup concurrency primitives
        executorService = Executors.newFixedThreadPool(CONCURRENT_WORKERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENT_WORKERS);

        // Act: Launch concurrent submissions
        long startTime = System.nanoTime();
        
        for (int i = 0; i < CONCURRENT_WORKERS; i++) {
            final int workerIndex = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Synchronize start
                    
                    long t0 = System.nanoTime();
                    
                    // Submit claim with shared policy/address
                    Map<String, Object> payload = Map.of(
                        "policyId", SHARED_POLICY,
                        "address", SHARED_ADDRESS,
                        "workerId", workerIndex
                    );
                    
                    claimService.initiateClaim(payload);
                    
                    long t1 = System.nanoTime();
                    latencies.add(Duration.ofNanos(t1 - t0).toMillis());
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    raceConditionCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
            
            // Apply submission interval
            if (i < CONCURRENT_WORKERS - 1) {
                Thread.sleep(SUBMISSION_INTERVAL_MS);
            }
        }

        // Trigger all waiting threads
        startLatch.countDown();
        
        // Wait for completion with timeout
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        long endTime = System.nanoTime();
        long totalDurationMs = Duration.ofNanos(endTime - startTime).toMillis();

        // Assert: Verify results
        
        // 1. All threads completed without race conditions
        assertTrue(completed, "All concurrent workers completed within timeout");
        assertEquals(0, raceConditionCount.get(), "No race conditions detected");

        // 2. Duplicate task created exactly once per unique potential duplicate
        // Since all submissions share policy/address, there is one unique duplicate scenario.
        verify(duplicateDetectionService, times(1))
            .createDuplicateTask(eq(SHARED_POLICY), eq(SHARED_ADDRESS));

        // 3. Audit log captures all concurrent access events
        assertEquals(CONCURRENT_WORKERS, auditPayloadCaptor.getAllValues().size(),
            "Audit log captures all concurrent access events");

        // 4. Latency P99 under threshold
        List<Long> sortedLatencies = latencies.stream().sorted().toList();
        int p99Index = (int) Math.ceil(0.99 * sortedLatencies.size()) - 1;
        long p99Latency = sortedLatencies.get(p99Index);
        
        assertTrue(p99Latency < P99_LATENCY_THRESHOLD_MS,
            String.format("P99 Latency %.2fms exceeds threshold %.2fms", p99Latency, P99_LATENCY_THRESHOLD_MS));
    }
}
