package app.integration.performance;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

@DisplayName("LargePayloadStandardization")
class LargePayloadStandardizationPerformanceTest {

    private static final int PAYLOAD_SIZE_KB = 200;
    private static final int CONCURRENT_REQUESTS = 200;
    private static final double MAX_LATENCY_MS = 1500.0;
    private static final double MIN_THROUGHPUT_RPS = 130.0;
    private static final double MAX_MALFORMED_LATENCY_MS = 100.0;
    private static final Logger LOG = Logger.getLogger(LargePayloadStandardizationPerformanceTest.class.getName());

    @Test
    @DisplayName("validate_large_payload_data_standardization_latency")
    void validateLargePayloadDataStandardizationLatency() throws Exception {
        // Mock external I/O contracts (S3 & DynamoDB) to avoid live calls
        DocumentStoreMock s3Mock = mock(DocumentStoreMock.class);
        DataStoreMock dynamoMock = mock(DataStoreMock.class);
        ClaimStandardizationService service = new ClaimStandardizationService(s3Mock, dynamoMock);

        // Generate 200KB payload with complex reporter & document metadata
        Map<String, Object> payload = generatePayload(PAYLOAD_SIZE_KB, "attorney");

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    boolean result = service.validateAndStandardize(payload, "gdpr_minimization");
                    long end = System.nanoTime();
                    long latencyMs = (end - start) / 1_000_000;
                    totalLatency.addAndGet(latencyMs);
                    if (result) successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        long wallStart = System.nanoTime();
        startLatch.countDown();
        endLatch.await(15, TimeUnit.SECONDS);
        long wallEnd = System.nanoTime();
        executor.shutdown();

        double avgLatency = totalLatency.get() / (double) CONCURRENT_REQUESTS;
        double throughput = (CONCURRENT_REQUESTS * 1_000_000_000.0) / (wallEnd - wallStart);

        LOG.info(String.format("Performance: Avg Latency=%.2fms | Throughput=%.2f rps | Successes=%d",
                avgLatency, throughput, successCount.get()));

        assertTrue(avgLatency <= MAX_LATENCY_MS,
                String.format("Avg latency %.2fms exceeds %.2fms limit", avgLatency, MAX_LATENCY_MS));
        assertTrue(throughput >= MIN_THROUGHPUT_RPS,
                String.format("Throughput %.2f rps below %.2f rps target", throughput, MIN_THROUGHPUT_RPS));
        assertEquals(CONCURRENT_REQUESTS, successCount.get(), "All concurrent requests should succeed");
    }

    @Test
    @DisplayName("input_validation_rejects_malformed_fields_immediately")
    void inputValidationRejectsMalformedFieldsImmediately() throws Exception {
        DocumentStoreMock s3Mock = mock(DocumentStoreMock.class);
        DataStoreMock dynamoMock = mock(DataStoreMock.class);
        ClaimStandardizationService service = new ClaimStandardizationService(s3Mock, dynamoMock);

        Map<String, Object> malformedPayload = new HashMap<>();
        malformedPayload.put("id", "123");
        malformedPayload.put("invalid_field", "bad_value");

        long start = System.nanoTime();
        try {
            service.validateAndStandardize(malformedPayload, "gdpr_minimization");
            fail("Should reject malformed input immediately");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        long end = System.nanoTime();
        double latency = (end - start) / 1_000_000.0;

        assertTrue(latency < MAX_MALFORMED_LATENCY_MS,
                String.format("Malformed validation took %.2fms, expected < %.2fms", latency, MAX_MALFORMED_LATENCY_MS));
    }

    private Map<String, Object> generatePayload(int sizeKb, String reporterType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", UUID.randomUUID().toString());
        payload.put("reporter_type", reporterType);
        payload.put("pii_data", generateString(sizeKb * 100));
        payload.put("document_metadata", Map.of("bucket", "Document & Media Store-bucket", "key", "claim_123.json"));
        return payload;
    }

    private String generateString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append('x');
        return sb.toString();
    }

    // Mock I/O Contracts
    interface DocumentStoreMock {
        String upload(String bucket, String key, byte[] data);
    }

    interface DataStoreMock {
        void putItem(String table, Map<String, Object> item);
    }

    static class ClaimStandardizationService {
        private final DocumentStoreMock s3;
        private final DataStoreMock dynamo;

        ClaimStandardizationService(DocumentStoreMock s3, DataStoreMock dynamo) {
            this.s3 = s3;
            this.dynamo = dynamo;
        }

        boolean validateAndStandardize(Map<String, Object> payload, String compliance) {
            if (!payload.containsKey("id") || !payload.containsKey("payload")) {
                throw new IllegalArgumentException("Missing required fields: id, payload");
            }
            // GDPR PII masking/tokenization simulation
            if ("gdpr_minimization".equals(compliance) && payload.containsKey("pii_data")) {
                payload.put("pii_data", "***GDPR_TOKENIZED***");
            }
            // Simulate S3 & DynamoDB I/O (mocked, so zero-latency infra call)
            s3.upload("bucket", "key", new byte[0]);
            dynamo.putItem("Policy & Claim Data Store_table", payload);
            return true;
        }
    }
}
