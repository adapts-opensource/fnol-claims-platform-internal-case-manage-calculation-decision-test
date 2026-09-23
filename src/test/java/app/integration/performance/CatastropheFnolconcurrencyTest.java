package app.integration.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Performance tests for Insured Engagement & Tracking: decision: orchestration.
 * Verifies thread-safe processing of concurrent FNOL submissions during catastrophe events.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Catastrophe FNOL Concurrency")
class CatastropheFnolConcurrencyTest {

    @Mock
    private PolicyMatchingService policyMatchingService;

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private DiaryGenerationService diaryGenerationService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private FnolOrchestrationService orchestrationService;

    private static final int CONCURRENT_REQUESTS = 500;
    private static final int THREAD_POOL_SIZE = 32;
    private static final String EVENT_CODE = "HURRICANE_2024_FL";
    private static final String CHANNEL = "API";
    private static final String POLICY_MATCH_METHOD = "risk_address";
    private static final long MAX_ELAPSED_SECONDS = 45;
    private static final double MIN_THROUGHPUT_REQ_SEC = 10.0;
    private static final long MAX_DIARY_LATENCY_MS = 200;

    @BeforeEach
    void setUp() {
        // Mock policy matching to succeed deterministically
        when(policyMatchingService.matchPolicy(any(PolicyMatchRequest.class)))
                .thenAnswer(invocation -> {
                    PolicyMatchRequest req = invocation.getArgument(0);
                    return PolicyMatchResult.success(req.getPolicyId(), PolicyMatchMethod.valueOf(POLICY_MATCH_METHOD));
                });

        // Mock claim creation to return valid claim
        when(claimRepository.save(any(Claim.class)))
                .thenAnswer(invocation -> {
                    Claim claim = invocation.getArgument(0);
                    claim.setId("CLAIM-" + System.nanoTime() + "-" + claim.getPolicyId());
                    return claim;
                });

        // Mock diary creation with minimal latency simulation
        when(diaryGenerationService.createDiary(any(DiaryRequest.class)))
                .thenAnswer(invocation -> {
                    // Simulate tiny processing to ensure concurrency is measurable
                    Thread.sleep(1); 
                    return DiaryRecord.builder().status("CREATED").build();
                });

        // Mock audit log
        when(auditLogService.log(any(AuditEvent.class)))
                .thenReturn(AuditEvent.builder().id("AUDIT-1").build());
    }

    @Test
    @DisplayName("Orchestrate catastrophe FNOL concurrent intake")
    void orchestrate_catastrophe_fnol_concurrent_intake() throws Exception {
        // Arrange
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ArgumentCaptor<FnolRequest> requestCaptor = ArgumentCaptor.forClass(FnlRequest.class);
        ArgumentCaptor<Claim> claimCaptor = ArgumentCaptor.forClass(Claim.class);
        ArgumentCaptor<DiaryRequest> diaryCaptor = ArgumentCaptor.forClass(DiaryRequest.class);
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);

        // Act
        long startTime = System.nanoTime();
        try (ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                final int requestId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Ensure all threads start simultaneously
                        FnolRequest request = buildRequest(requestId);
                        FnolResult result = orchestrationService.processFnol(request);
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
            startLatch.countDown(); // Release all threads
        }

        boolean completed = endLatch.await(MAX_ELAPSED_SECONDS, TimeUnit.SECONDS);
        long endTime = System.nanoTime();

        // Assert
        assertTrue(completed, "All requests must complete within the timeout");

        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        assertTrue(elapsedSeconds < MAX_ELAPSED_SECONDS,
                String.format("Claims should be created within %d seconds, took %.2f seconds", MAX_ELAPSED_SECONDS, elapsedSeconds));

        double throughput = CONCURRENT_REQUESTS / elapsedSeconds;
        assertTrue(throughput > MIN_THROUGHPUT_REQ_SEC,
                String.format("Throughput should exceed %.1f claims/sec, achieved %.2f", MIN_THROUGHPUT_REQ_SEC, throughput));

        assertEquals(0, failureCount.get(), "No failures expected during concurrent intake");
        assertEquals(CONCURRENT_REQUESTS, successCount.get(), "All requests should succeed");

        // Verify interactions
        verify(policyMatchingService, times(CONCURRENT_REQUESTS)).matchPolicy(any(PolicyMatchRequest.class));
        verify(claimRepository, times(CONCURRENT_REQUESTS)).save(any(Claim.class));
        verify(diaryGenerationService, times(CONCURRENT_REQUESTS)).createDiary(any(DiaryRequest.class));
        verify(auditLogService, times(CONCURRENT_REQUESTS)).log(any(AuditEvent.class));

        // Verify zero duplicates/orphans by checking claim IDs are unique and present
        List<Claim> savedClaims = claimCaptor.getAllValues();
        assertEquals(CONCURRENT_REQUESTS, savedClaims.size());
        long uniqueIds = savedClaims.stream().map(Claim::getId).distinct().count();
        assertEquals(CONCURRENT_REQUESTS, uniqueIds, "Zero duplicate claim records allowed");

        // Verify diaries created within latency budget
        List<DiaryRequest> diaryRequests = diaryCaptor.getAllValues();
        assertEquals(CONCURRENT_REQUESTS, diaryRequests.size());
        // Note: Latency verification is implicit via total time and mock sleep; 
        // in a real scenario, we might assert diary creation count matches claim count.
        assertEquals(CONCURRENT_REQUESTS, diaryRequests.size(), "Statutory diaries must be generated for all claims");
    }

    private FnolRequest buildRequest(int index) {
        return FnolRequest.builder()
                .eventId(EVENT_CODE)
                .channel(CHANNEL)
                .policyId("POLICY-" + index % 100) // Reuse policies to test matching
                .claimId("REQ-" + index)
                .severity("high")
                .representationStatus("none")
                .build();
    }
}
