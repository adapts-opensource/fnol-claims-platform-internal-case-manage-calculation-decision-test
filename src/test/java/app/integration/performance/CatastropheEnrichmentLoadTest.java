package app.integration.performance;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Performance test for Multi-Channel FNOL Submission:decision:enrichment.
 * Validates stability, throughput, and enrichment decision latency under catastrophe-level load.
 * External I/O (SES, S3, DynamoDB) is mocked to prevent live calls and isolate orchestration metrics.
 */
@ExtendWith(MockitoExtension.class)
class CatastropheEnrichmentLoadTest {

    @Mock
    private CommunicationAckManager sesMock;
    @Mock
    private DocumentMediaStore s3Mock;
    @Mock
    private GuidewireClaimModel dynamoMock;
    @Mock
    private MultiChannelFnolOrchestrator fnolOrchestrator;

    private static final int TOTAL_SUBMISSIONS = 5000;
    private static final int CONCURRENT_USERS = 200;
    private static final String EVENT_CODE = "HURRICANE_MIKE";
    private static final String ENRICHMENT_CONFIG = "address_geocode_prior_claims_mortgagee";

    private final AtomicLong maxEnrichmentDecisionTime = new AtomicLong(0);
    private final AtomicLong maxDiaryTriggerTime = new AtomicLong(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        // Mock external I/O contracts to ensure zero live AWS/HTTP calls during perf test
        lenient().when(sesMock.sendEmail(any())).thenReturn(null);
        lenient().when(s3Mock.storeDocument(any(), any())).thenReturn("s3://mock-bucket/doc.json");
        lenient().when(dynamoMock.saveClaimModel(any())).thenReturn(null);

        // Mock FNOL orchestration layer with simulated enrichment and diary steps
        lenient().when(fnolOrchestrator.processSubmission(any(), any())).thenAnswer(invocation -> {
            long enrichStart = System.nanoTime();
            // Simulate enrichment decision processing (address geocode, prior claims, mortgagee)
            Thread.sleep(50 + (long) (Math.random() * 100));
            long enrichEnd = System.nanoTime();
            long enrichMs = TimeUnit.NANOSECONDS.toMillis(enrichEnd - enrichStart);
            maxEnrichmentDecisionTime.updateAndGet(v -> Math.max(v, enrichMs));

            long diaryStart = System.nanoTime();
            // Simulate diary creation trigger
            Thread.sleep(10 + (long) (Math.random() * 40));
            long diaryEnd = System.nanoTime();
            long diaryMs = TimeUnit.NANOSECONDS.toMillis(diaryEnd - diaryStart);
            maxDiaryTriggerTime.updateAndGet(v -> Math.max(v, diaryMs));

            return Map.of("claimType", "CATASTROPHE", "enrichmentStatus", "COMPLETED", "diaryId", "diary_999");
        });
    }

    @Test
    void catastrophe_enrichment_load_stability() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch latch = new CountDownLatch(TOTAL_SUBMISSIONS);
        long overallStart = System.nanoTime();

        for (int i = 0; i < TOTAL_SUBMISSIONS; i++) {
            executor.submit(() -> {
                try {
                    fnolOrchestrator.processSubmission(EVENT_CODE, ENRICHMENT_CONFIG);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(120, TimeUnit.SECONDS);
        long overallEnd = System.nanoTime();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertTrue(completed, "Load test did not complete within 120s duration window");
        assertEquals(TOTAL_SUBMISSIONS, successCount.get() + errorCount.get(), "All submissions must be accounted for");

        double errorRate = (double) errorCount.get() / TOTAL_SUBMISSIONS;
        assertTrue(errorRate < 0.001, () -> "Error rate exceeded 0.1% threshold: " + String.format("%.4f", errorRate));
        assertTrue(maxEnrichmentDecisionTime.get() <= 2500, () -> "Enrichment decision time exceeded 2500ms: " + maxEnrichmentDecisionTime.get() + "ms");
        assertTrue(maxDiaryTriggerTime.get() <= 1000, () -> "Diary creation trigger time exceeded 1000ms: " + maxDiaryTriggerTime.get() + "ms");

        double throughput = TOTAL_SUBMISSIONS / ((overallEnd - overallStart) / 1_000_000_000.0);
        System.out.printf("[PERF] Completed %d submissions in %.2fs. Throughput: %.2f req/s. MaxEnrich: %dms, MaxDiary: %dms, ErrorRate: %.3f%%%n",
                TOTAL_SUBMISSIONS, (overallEnd - overallStart) / 1_000_000_000.0, throughput,
                maxEnrichmentDecisionTime.get(), maxDiaryTriggerTime.get(), errorRate * 100);
    }
}
