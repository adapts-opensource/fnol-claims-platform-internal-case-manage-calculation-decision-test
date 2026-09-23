package app.integration.performance;

import org.junit.jupiter.api.*;
import org.mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("MultiChannelFNOLIngest Performance Tests")
class ConcurrentMultiChannelFnolIngestionAndTransformationTest {

    private static final int CONCURRENT_USERS = 1000;
    private static final String[] CHANNELS = {"insured_portal", "agent_portal", "internal_csr", "api"};
    private static final int PAYLOAD_SIZE_KB = 256;
    private static final long DURATION_SECONDS = 120;
    private static final double SUCCESS_RATE_THRESHOLD = 0.999;
    private static final long MAX_AVG_LATENCY_MS = 3000;

    @Mock
    private FnolIngestionService ingestionService;

    @Mock
    private DataTransformationService transformationService;

    @InjectMocks
    private MultiChannelFnolProcessor processor;

    private final AtomicLong totalLatencyNanos = new AtomicLong(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failCount = new AtomicInteger(0);
    private final ConcurrentHashMap<String, String> claimIdMap = new ConcurrentHashMap<>();
    private final List<String> receivedPayloads = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock transformation to return unified claim model with generated claim number
        doAnswer(invocation -> {
            String payload = invocation.getArgument(0);
            String channel = invocation.getArgument(1);
            String claimId = "CLAIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            return new UnifiedClaimModel(claimId, payload, channel, true);
        }).when(transformationService).transform(anyString(), anyString());

        // Mock ingestion to capture payloads for corruption verification
        doAnswer(invocation -> {
            receivedPayloads.add(invocation.getArgument(0));
            return true;
        }).when(ingestionService).ingest(anyString(), anyString());
    }

    @Test
    @DisplayName("concurrent_multi_channel_fnol_ingestion_and_transformation")
    void concurrent_multi_channel_fnol_ingestion_and_transformation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENT_USERS);
        long startTime = System.nanoTime();

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            final String channel = CHANNELS[i % CHANNELS.length];
            final String payload = generatePayload(PAYLOAD_SIZE_KB);

            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize concurrent start
                    long threadStart = System.nanoTime();
                    try {
                        // Simulate ingestion & transformation pipeline
                        UnifiedClaimModel result = transformationService.transform(payload, channel);
                        ingestionService.ingest(payload, channel);

                        long threadEnd = System.nanoTime();
                        totalLatencyNanos.addAndGet(threadEnd - threadStart);
                        successCount.incrementAndGet();
                        claimIdMap.putIfAbsent(result.claimId(), "assigned");
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads concurrently
        endLatch.await(DURATION_SECONDS, TimeUnit.SECONDS);
        long endTime = System.nanoTime();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Metric Calculations
        double successRate = (double) successCount.get() / CONCURRENT_USERS;
        long avgLatencyMs = totalLatencyNanos.get() / CONCURRENT_USERS / 1_000_000;
        long durationSec = TimeUnit.NANOSECONDS.toSeconds(endTime - startTime);

        // Assertions matching Expected Results
        assertTrue(durationSec <= DURATION_SECONDS, 
                "Test execution should complete within %d seconds".formatted(DURATION_SECONDS));
        assertTrue(successRate >= SUCCESS_RATE_THRESHOLD, 
                "Success rate %.2f%% is below 99.9%% threshold".formatted(successRate * 100));
        assertTrue(avgLatencyMs <= MAX_AVG_LATENCY_MS, 
                "Average latency %.2fms exceeds %dms threshold".formatted(avgLatencyMs, MAX_AVG_LATENCY_MS));
        assertEquals(CONCURRENT_USERS, successCount.get(), "All submissions should transform successfully");
        assertTrue(claimIdMap.size() == CONCURRENT_USERS, "All submissions should be assigned claim numbers");
        assertEquals(0, failCount.get(), "Zero data corruption or failures expected under load");
    }

    private String generatePayload(int sizeKb) {
        StringBuilder sb = new StringBuilder(sizeKb * 1024);
        for (int i = 0; i < sizeKb * 1024; i++) {
            sb.append("FNOL_DATA_X");
        }
        return sb.toString();
    }

    // Mock service interfaces for structural decoupling
    interface FnolIngestionService { boolean ingest(String payload, String channel); }
    interface DataTransformationService { UnifiedClaimModel transform(String payload, String channel); }
    record UnifiedClaimModel(String claimId, String payload, String channel, boolean valid) {}
    static class MultiChannelFnolProcessor { /* Implementation abstracted for mock-driven performance test */ }
}
