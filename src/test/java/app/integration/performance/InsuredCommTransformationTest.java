package app.integration.performance;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@DisplayName("InsuredCommTransformation")
class InsuredCommTransformation {

    private static final int FNOL_PAYLOADS = 200;
    private static final int TARGET_THROUGHPUT_RPS = 50;
    private static final long MAX_LATENCY_PER_PAYLOAD_MS = 1500;
    private static final long MAX_QUEUEING_LATENCY_MS = 2000;
    private static final String[] CHANNELS = {"email", "sms", "portal_notification"};

    private InMemoryTransformationPipeline pipeline;
    private InMemoryCommunicationQueue queue;
    private InMemoryTrackingStore trackingStore;

    @BeforeEach
    void setUp() {
        pipeline = new InMemoryTransformationPipeline();
        queue = new InMemoryCommunicationQueue();
        trackingStore = new InMemoryTrackingStore();
    }

    @Test
    @DisplayName("insured_engagement_communication_transformation_latency")
    void insured_engagement_communication_transformation_latency() throws Exception {
        List<FnolPayload> payloads = generatePayloads(FNOL_PAYLOADS);

        long startTime = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        CountDownLatch latch = new CountDownLatch(FNOL_PAYLOADS);
        AtomicLong maxPayloadLatency = new AtomicLong(0);
        AtomicLong maxQueueLatency = new AtomicLong(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (FnolPayload payload : payloads) {
            executor.submit(() -> {
                long payloadStart = System.nanoTime();
                try {
                    TransformationResult result = pipeline.transform(payload, "high", "exponential_backoff");
                    long payloadLatency = (System.nanoTime() - payloadStart) / 1_000_000;
                    maxPayloadLatency.updateAndGet(v -> Math.max(v, payloadLatency));

                    long queueStart = System.nanoTime();
                    queue.enqueue(result.toCommunicationTask());
                    long queueLatency = (System.nanoTime() - queueStart) / 1_000_000;
                    maxQueueLatency.updateAndGet(v -> Math.max(v, queueLatency));

                    trackingStore.update(result.getTrackingId(), "queued");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        long totalTimeMs = (System.nanoTime() - startTime) / 1_000_000;

        double actualThroughput = completed ? (FNOL_PAYLOADS / (totalTimeMs / 1000.0)) : 0;

        Assertions.assertTrue(maxPayloadLatency.get() < MAX_LATENCY_PER_PAYLOAD_MS,
                "Max payload latency " + maxPayloadLatency.get() + "ms exceeds SLA " + MAX_LATENCY_PER_PAYLOAD_MS + "ms");
        Assertions.assertTrue(maxQueueLatency.get() < MAX_QUEUEING_LATENCY_MS,
                "Max queue latency " + maxQueueLatency.get() + "ms exceeds SLA " + MAX_QUEUEING_LATENCY_MS + "ms");
        Assertions.assertTrue(successCount.get() == FNOL_PAYLOADS,
                "All payloads should be processed successfully");
        Assertions.assertTrue(failureCount.get() == 0,
                "No failures expected during transformation and queuing");
        Assertions.assertTrue(actualThroughput >= TARGET_THROUGHPUT_RPS,
                "Throughput " + String.format("%.2f", actualThroughput) + " req/sec is below target " + TARGET_THROUGHPUT_RPS + " req/sec");
        Assertions.assertTrue(trackingStore.getUpdatedCount() == FNOL_PAYLOADS,
                "All delivery tracking records should be updated accurately");
    }

    private List<FnolPayload> generatePayloads(int count) {
        List<FnolPayload> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new FnolPayload("claim_" + i, CHANNELS[i % CHANNELS.length], "high_complexity"));
        }
        return list;
    }

    // Mock: FNOL Intake Payload
    static class FnolPayload {
        final String claimId;
        final String channel;
        final String templateComplexity;
        FnolPayload(String claimId, String channel, String templateComplexity) {
            this.claimId = claimId;
            this.channel = channel;
            this.templateComplexity = templateComplexity;
        }
    }

    // Mock: Transformation Result
    static class TransformationResult {
        final String trackingId;
        final String taskPayload;
        TransformationResult(String trackingId, String taskPayload) {
            this.trackingId = trackingId;
            this.taskPayload = taskPayload;
        }
        String getTrackingId() { return trackingId; }
        CommunicationTask toCommunicationTask() { return new CommunicationTask(trackingId, taskPayload); }
    }

    // Mock: Communication Task
    static class CommunicationTask {
        final String trackingId;
        final String payload;
        CommunicationTask(String trackingId, String payload) {
            this.trackingId = trackingId;
            this.payload = payload;
        }
    }

    // Mock: External Transformation Service (S3/DynamoDB/HSM abstraction)
    static class InMemoryTransformationPipeline {
        TransformationResult transform(FnolPayload payload, String complexity, String retryPolicy) {
            // Simulate high-complexity template resolution & data enrichment
            try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new TransformationResult("track_" + payload.claimId, "task_" + payload.claimId);
        }
    }

    // Mock: External Communication Queue (SQS/Kafka abstraction)
    static class InMemoryCommunicationQueue {
        void enqueue(CommunicationTask task) {
            // Simulate async queue push with bounded buffer
        }
    }

    // Mock: External Tracking Store (DynamoDB/S3 audit abstraction)
    static class InMemoryTrackingStore {
        private final AtomicInteger updatedCount = new AtomicInteger(0);
        void update(String trackingId, String status) {
            // Simulate idempotent write with retry/backoff logic
            updatedCount.incrementAndGet();
        }
        int getUpdatedCount() { return updatedCount.get(); }
    }
}
