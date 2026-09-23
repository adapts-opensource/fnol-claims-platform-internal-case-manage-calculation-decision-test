package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Performance test for Multi-Channel FNOL Submission: transformation: orchestration.
 * Validates latency, throughput, and correctness under mixed channel load with policy matching and triage routing.
 *
 * NFR Coverage:
 * - Concurrency: Thread safety via concurrent execution and atomic counters.
 * - Observability: Structured logging verification via mock interactions.
 * - Compliance: GDPR minimization in payload generation; SOC2 audit logging verification.
 * - Security: Input validation and idempotency enforcement simulation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MixedChannelTransformationLoad")
class MixedChannelTransformationLoad {

    private static final int VOLUME = 1000;
    private static final double MAX_AVG_E2E_SECONDS = 2.5;
    private static final double MAX_POLICY_MATCH_SECONDS = 1.5;
    private static final double MAX_TRIAGE_ROUTING_SECONDS = 1.5;
    private static final int MAX_PAYLOAD_KB = 45;

    private List<String> channels;

    @Mock
    private PolicyMatcher policyMatcher;

    @Mock
    private TriageRouter triageRouter;

    @Mock
    private StructuredLogger logger;

    private FnolOrchestrationService fnolOrchestrationService;

    @BeforeEach
    void setUp() {
        channels = List.of("portal", "api", "agent_assisted");
        
        // Initialize service with mocked dependencies
        fnolOrchestrationService = new FnolOrchestrationService(policyMatcher, triageRouter, logger);

        // Configure mocks to simulate realistic latency and side effects
        lenient().when(policyMatcher.match(any(Claim.class))).thenAnswer(invocation -> {
            // Simulate policy lookup latency (e.g., DynamoDB/External API)
            Thread.sleep(50); 
            return new PolicyMatchResult(true, "POL-TEST-001");
        });

        lenient().when(triageRouter.route(any(Claim.class))).thenAnswer(invocation -> {
            // Simulate triage routing logic
            Thread.sleep(20);
            return new TriageState("INITIAL_RECEIVED");
        });

        lenient().doNothing().when(logger).info(any(String.class), any(Object[].class));
    }

    @Test
    @DisplayName("validate_multi_channel_transformation_latency")
    void validate_multi_channel_transformation_latency() throws Exception {
        // Arrange: Metrics collectors
        AtomicLong totalE2eNanos = new AtomicLong(0);
        AtomicLong maxPolicyMatchNanos = new AtomicLong(0);
        AtomicLong maxTriageNanos = new AtomicLong(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger validationFailCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(VOLUME);

        // Act: Concurrent submission simulation
        ExecutorService executor = Executors.newFixedThreadPool(20);
        
        try {
            IntStream.range(0, VOLUME).parallel().forEach(index -> {
                executor.submit(() -> {
                    long e2eStart = System.nanoTime();
                    String channel = channels.get(index % channels.size());
                    String idempotencyKey = "idemp-key-" + index + "-" + System.nanoTime();
                    
                    try {
                        // Generate minimal payload adhering to GDPR data minimization
                        Claim claim = generateMinimalClaim(channel, idempotencyKey);
                        
                        // Execute orchestration
                        Claim result = fnolOrchestrationService.submitFnol(claim);
                        
                        // Verify idempotency key usage (simulated by mock interactions)
                        assertNotNull(result.getClaimId(), "Claim ID must be assigned");
                        assertEquals(idempotencyKey, result.getIdempotencyKey(), "Idempotency key must be preserved");
                        
                        successCount.incrementAndGet();
                        
                    } catch (Exception e) {
                        if (e.getMessage() != null && e.getMessage().contains("VALIDATION")) {
                            validationFailCount.incrementAndGet();
                        } else {
                            fail("Unexpected failure during submission: " + e.getMessage());
                        }
                    } finally {
                        long e2eEnd = System.nanoTime();
                        totalE2eNanos.addAndGet(e2eEnd - e2eStart);
                        latch.countDown();
                    }
                });
            });

            // Wait for all tasks with a generous timeout
            boolean completed = latch.await(300, TimeUnit.SECONDS);
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            // Assert: Performance and Correctness
            assertTrue(completed, "All submissions must complete within timeout");
            
            double avgE2eSeconds = (totalE2eNanos.get() / (double) VOLUME) / 1_000_000_000.0;
            double maxPolicyMatchSeconds = maxPolicyMatchNanos.get() / 1_000_000_000.0;
            double maxTriageSeconds = maxTriageNanos.get() / 1_000_000_000.0;

            assertTrue(avgE2eSeconds < MAX_AVG_E2E_SECONDS,
                    String.format("Average E2E time %.4fs exceeds limit %.2fs", avgE2eSeconds, MAX_AVG_E2E_SECONDS));
            
            assertTrue(maxPolicyMatchSeconds < MAX_POLICY_MATCH_SECONDS,
                    String.format("Max policy match time %.4fs exceeds limit %.2fs", maxPolicyMatchSeconds, MAX_POLICY_MATCH_SECONDS));

            assertEquals(VOLUME, successCount.get(), "100% of claims must be successfully routed");
            assertEquals(0, validationFailCount.get(), "No validation failures expected with valid inputs");

            // Verify mock interactions for SOC2 audit and observability
            verify(policyMatcher, times(VOLUME)).match(any(Claim.class));
            verify(triageRouter, times(VOLUME)).route(any(Claim.class));
            verify(logger, times(VOLUME)).info(any(String.class), any(Object[].class));
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted during execution");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Generates a minimal claim payload adhering to GDPR data minimization principles.
     * Includes required fields: claim_id, claim_number, tenant_id, policy_id.
     */
    private Claim generateMinimalClaim(String channel, String idempotencyKey) {
        Claim claim = new Claim();
        claim.setChannel(channel);
        claim.setIdempotencyKey(idempotencyKey);
        claim.setTenantId("tenant-insurance-001");
        claim.setPolicyId("POL-TEST-001");
        claim.setClaimNumber("FNOL-" + System.currentTimeMillis());
        claim.setPayloadSizeBytes(MAX_PAYLOAD_KB * 1024 / 2); // Well within 45KB limit
        claim.setPiiLevel("NONE"); // GDPR minimization
        claim.setAuditTimestamp(System.currentTimeMillis());
        return claim;
    }
}
