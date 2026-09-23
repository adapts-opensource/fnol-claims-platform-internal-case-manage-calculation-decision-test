package app.integration.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Performance test for Claim Data Standardization:orchestration:decision.
 * Validates sustained throughput, policy match distribution, thread safety,
 * and audit logging under simulated load without live infrastructure.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Sustained Throughput Policy Match Decisions")
class SustainedThroughputPolicyMatchDecisionsTest {

    private static final int REQUESTS_PER_MINUTE = 500;
    private static final int DURATION_MINUTES = 10;
    private static final int TOTAL_REQUESTS = REQUESTS_PER_MINUTE * DURATION_MINUTES;
    private static final int THREAD_COUNT = 10;
    private static final double EXPECTED_MATCH_RATE = 0.8;
    private static final long MAX_LATENCY_MS = 100;
    private static final double LATENCY_VARIANCE_THRESHOLD = 0.05;

    @Mock
    private PolicyLookupService policyLookupService;

    @Mock
    private ClaimDataRepository claimDataRepository;

    @Mock
    private RulesAndTriageService rulesAndTriageService;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private OrchestrationDecisionService orchestrationService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> auditLogCaptor;

    private int requestCounter;

    @BeforeEach
    void setUp() {
        requestCounter = 0;
        
        // Mock policy lookup to return 80% match, 20% null (Unmatched)
        when(policyLookupService.lookupPolicy(anyString(), anyString()))
            .thenAnswer(invocation -> {
                int count = requestCounter++;
                boolean isMatch = (count % 5) != 0; // 80% match (1 in 5 is not match)
                if (isMatch) {
                    return new PolicyMatchResult("POL-123", "AUTO", true);
                }
                return null;
            });

        // Mock repository saves
        when(claimDataRepository.save(anyMap())).thenAnswer(inv -> inv.getArgument(0));
        
        // Mock rules service for matched claims
        when(rulesAndTriageService.routeClaim(anyString(), anyString())).thenReturn("TRIAGE-001");
    }

    @Test
    @DisplayName("fnol_orchestration_sustained_throughput_policy_match")
    void fnol_orchestration_sustained_throughput_policy_match() throws Exception {
        // Arrange: Setup metrics collectors
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);
        
        AtomicLong totalLatencyNanos = new AtomicLong(0);
        AtomicLong maxLatencyNanos = new AtomicLong(0);
        AtomicLong latencySquaredSum = new AtomicLong(0);
        AtomicInteger matchCount = new AtomicInteger(0);
        AtomicInteger shellCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        // Act: Simulate sustained load with concurrency
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            executor.submit(() -> {
                long start = System.nanoTime();
                try {
                    Map<String, Object> claimPayload = createClaimPayload(i);
                    OrchestrationResult result = orchestrationService.processDecision(claimPayload);
                    
                    long elapsed = System.nanoTime() - start;
                    totalLatencyNanos.addAndGet(elapsed);
                    maxLatencyNanos.updateAndGet(v -> Math.max(v, elapsed));
                    latencySquaredSum.addAndGet(elapsed * elapsed);
                    
                    if (result.isPolicyMatched()) {
                        matchCount.incrementAndGet();
                        verify(rulesAndTriageService).routeClaim(anyString(), anyString());
                    } else {
                        shellCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    errorCount.incrementAndGet();
                    if (firstError.compareAndSet(null, t)) {
                        // Capture first error for detailed assertion if needed
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all requests to complete
        boolean completed = latch.await(DURATION_MINUTES + 1, TimeUnit.MINUTES);
        executor.shutdownNow();

        // Assert: Verify structural integrity and no timeouts/drops
        assertTrue(completed, "All requests completed within duration window");
        assertEquals(0, errorCount.get(), "No request timeouts or dropped connections");
        assertNull(firstError.get(), "No unexpected exceptions occurred");

        // Assert: Verify throughput stability and latency
        double avgLatencyMs = (double) totalLatencyNanos.get() / TOTAL_REQUESTS / 1_000_000;
        double maxLatencyMs = maxLatencyNanos.get() / 1_000_000.0;
        double variance = (latencySquaredSum.get() / TOTAL_REQUESTS) - (avgLatencyMs * avgLatencyMs);
        
        assertTrue(maxLatencyMs < MAX_LATENCY_MS, 
            "Max latency " + maxLatencyMs + "ms exceeds threshold " + MAX_LATENCY_MS + "ms");
        assertTrue(variance < LATENCY_VARIANCE_THRESHOLD, 
            "Latency variance " + variance + " indicates instability");

        // Assert: Verify policy match distribution (80% match, 20% shell)
        double actualMatchRate = (double) matchCount.get() / TOTAL_REQUESTS;
        assertTrue(Math.abs(actualMatchRate - EXPECTED_MATCH_RATE) < 0.02, 
            "Policy match rate " + actualMatchRate + " deviates significantly from " + EXPECTED_MATCH_RATE);
        assertEquals(TOTAL_REQUESTS, matchCount.get() + shellCount.get(), "All requests accounted for");

        // Assert: Verify audit logging captures all metrics
        verify(auditLogger, times(TOTAL_REQUESTS)).log(anyString(), anyString(), auditLogCaptor.capture());
        List<Map<String, Object>> allLogs = auditLogCaptor.getAllValues();
        assertEquals(TOTAL_REQUESTS, allLogs.size());
        
        // Verify structured logging contains throughput metrics
        Map<String, Object> sampleLog = allLogs.get(0);
        assertTrue(sampleLog.containsKey("request_id"));
        assertTrue(sampleLog.containsKey("policy_match_status"));
        assertTrue(sampleLog.containsKey("latency_ms"));
    }

    private Map<String, Object> createClaimPayload(int index) {
        return Map.of(
            "id", "CLM-" + String.format("%06d", index),
            "payload", Map.of(
                "submission_source", "portal_api_agent",
                "claim_type", "AUTO",
                "data_variance", index % 3 == 0 ? "high" : "low",
                "timestamp", System.currentTimeMillis()
            )
        );
    }

    // Minimal DTOs for simulation
    private record PolicyMatchResult(String policyId, String policyType, boolean isMatch) {}
    private record OrchestrationResult(String policyId, boolean isPolicyMatched, String triageId) {
        OrchestrationResult {
            isPolicyMatched = policyId != null;
        }
    }
}
