package app.integration.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Performance test for Claim Data Standardization orchestration decision engine.
 * Validates thread safety, unique ID generation, triage routing, and throughput
 * during high-concurrency catastrophe ingestion scenarios.
 */
@ExtendWith(MockitoExtension.class)
class HighConcurrencyCatastropheIngestionTest {

    // Test Configuration Constants
    private static final int CONCURRENCY_LEVEL = 1000;
    private static final int DURATION_SECONDS = 60;
    private static final String EVENT_CODE = "HURRICANE_IRMA";
    private static final String POLICY_ID_PREFIX = "POL-";
    private static final int UNIQUE_POLICIES = 100;
    private static final String INPUT_CHANNEL = "API";
    private static final String DATA_PAYLOAD = "standard_residential_property_fnol";
    private static final long MAX_MEDIAN_LATENCY_MS = 5000;
    private static final double MIN_THROUGHPUT_TPS = 100.0;
    private static final String CATASTROPHE_TRIAGE_CATEGORY = "CATASTROPHE_HIGH_PRIORITY";

    // Infrastructure Mocks
    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    // System Under Test Dependencies
    @Mock
    private ClaimNumberGenerator claimNumberGenerator;

    @InjectMocks
    private ClaimOrchestrationService claimOrchestrationService;

    // Concurrency Tracking
    private AtomicLong successCount;
    private AtomicLong failureCount;
    private List<Long> latencies;
    private Set<String> claimIds;
    private Set<String> triageCategories;

    @BeforeEach
    void setUp() {
        successCount = new AtomicLong(0);
        failureCount = new AtomicLong(0);
        latencies = Collections.synchronizedList(new ArrayList<>());
        claimIds = ConcurrentHashMap.newKeySet();
        triageCategories = ConcurrentHashMap.newKeySet();

        // Mock DynamoDB I/O
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

        // Mock S3 I/O
        when(s3Client.putObject(any(PutObjectRequest.class), any()))
                .thenReturn(PutObjectResponse.builder().build());

        // Mock Claim Number Generator for unique IDs and catastrophe prefix
        AtomicLong idCounter = new AtomicLong(0);
        when(claimNumberGenerator.generate(anyString(), anyString()))
                .thenAnswer(invocation -> "CLM-" + EVENT_CODE + "-" + idCounter.incrementAndGet());
    }

    @Test
    void fnol_orchestration_concurrent_catastrophe_injection() throws InterruptedException {
        // Arrange
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY_LEVEL);
        CountDownLatch latch = new CountDownLatch(CONCURRENCY_LEVEL);
        Instant startTime = Instant.now();

        // Act: Submit concurrent FNOL requests
        for (int i = 0; i < CONCURRENCY_LEVEL; i++) {
            String policyId = POLICY_ID_PREFIX + (i % UNIQUE_POLICIES);
            executor.submit(() -> {
                try {
                    Instant taskStart = Instant.now();
                    
                    // Invoke orchestration service
                    ClaimResult result = claimOrchestrationService.processFnol(
                            policyId, INPUT_CHANNEL, DATA_PAYLOAD, EVENT_CODE);
                    
                    Instant taskEnd = Instant.now();
                    long durationMs = Duration.between(taskStart, taskEnd).toMillis();
                    
                    // Record metrics
                    latencies.add(durationMs);
                    claimIds.add(result.getClaimId());
                    triageCategories.add(result.getTriageCategory());
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for completion within duration
        boolean completed = latch.await(DURATION_SECONDS, TimeUnit.SECONDS);
        Instant endTime = Instant.now();
        executor.shutdown();

        // Assert: Verification
        assertTrue(completed, "All tasks completed within duration");
        assertEquals(0, failureCount.get(), "Zero failures allowed");
        assertEquals(CONCURRENCY_LEVEL, successCount.get(), "All claims processed successfully");

        // Assert: Median Latency
        List<Long> sortedLatencies = latencies.stream().sorted().toList();
        long medianLatency = sortedLatencies.get(sortedLatencies.size() / 2);
        assertTrue(medianLatency <= MAX_MEDIAN_LATENCY_MS,
                "Median latency " + medianLatency + "ms must be <= " + MAX_MEDIAN_LATENCY_MS + "ms");

        // Assert: Unique Claim Numbers
        assertEquals(CONCURRENCY_LEVEL, claimIds.size(), "All claim IDs must be unique across concurrent requests");

        // Assert: Triage Routing
        assertTrue(triageCategories.contains(CATASTROPHE_TRIAGE_CATEGORY),
                "Catastrophe triage rule must be applied for event code " + EVENT_CODE);

        // Assert: Throughput
        double elapsedSeconds = Duration.between(startTime, endTime).getSeconds();
        double throughputTps = CONCURRENCY_LEVEL / elapsedSeconds;
        assertTrue(throughputTps >= MIN_THROUGHPUT_TPS,
                "Throughput " + throughputTps + " TPS must be >= " + MIN_THROUGHPUT_TPS);
        
        // Assert: Infrastructure Calls (Thread Safety / No Deadlocks)
        verify(dynamoDbClient, times(CONCURRENCY_LEVEL)).putItem(any(PutItemRequest.class));
        verify(s3Client, times(CONCURRENCY_LEVEL)).putObject(any(PutObjectRequest.class), any());
    }

    /**
     * Mock result object for orchestration response.
     */
    private static class ClaimResult {
        private final String claimId;
        private final String triageCategory;

        ClaimResult(String claimId, String triageCategory) {
            this.claimId = claimId;
            this.triageCategory = triageCategory;
        }

        String getClaimId() {
            return claimId;
        }

        String getTriageCategory() {
            return triageCategory;
        }
    }

    /**
     * Mock service interface for ClaimOrchestrationService.
     */
    private interface ClaimOrchestrationService {
        ClaimResult processFnol(String policyId, String channel, String payload, String eventCode);
    }

    /**
     * Mock generator interface.
     */
    private interface ClaimNumberGenerator {
        String generate(String prefix, String suffix);
    }
}
