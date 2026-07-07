package app.integration.performance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import app.integration.performance.service.AuditService;
import app.integration.performance.service.DynamoDbService;
import app.integration.performance.service.InsuredPortalService;
import app.integration.performance.service.S3Service;
import app.integration.performance.service.TransformValidateService;

/**
 * Performance test for Insured Engagement & Tracking:transformation:validation.
 * Validates system stability, data integrity, and throughput under concurrent
 * portal uploads during backend state transitions.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrentPortalUploadsValidationTest {

    @Mock
    private S3Service s3Service;

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private TransformValidateService transformValidateService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private InsuredPortalService insuredPortalService;

    private static final int ACTIVE_USERS = 100;
    private static final int CONCURRENT_UPLOADS = 50;
    private static final int UPLOAD_SIZE_BYTES = 2 * 1024 * 1024; // 2MB
    private static final String BACKEND_STATE = "Intake Review";
    private static final String UPLOAD_CHANNEL = "Insured Portal";
    private static final double LATENCY_SLA_SECONDS = 30.0;
    private static final double THROUGHPUT_SLA_PER_SEC = 5.0;

    @Test
    @DisplayName("test_concurrent_portal_uploads_during_state_transition")
    void test_concurrent_portal_uploads_during_state_transition() throws Exception {
        // Arrange: Mock behaviors to simulate successful infrastructure responses
        when(s3Service.uploadFile(anyString(), anyString(), any(InputStream.class)))
                .thenReturn("s3://Secure_Storage-bucket/Secure_Storage/{entity_id}.json");
        when(dynamoDbService.putItem(anyString(), anyMap())).thenReturn(true);
        when(dynamoDbService.updateItem(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(true);
        when(transformValidateService.validatePayload(anyMap())).thenReturn(true);
        when(transformValidateService.transformPayload(anyMap())).thenReturn(Map.of("status", "validated"));
        when(auditService.logEvent(any())).thenReturn(true);

        // Concurrency setup
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_UPLOADS);
        CountDownLatch latch = new CountDownLatch(ACTIVE_USERS);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> errors = List.of(); // Use thread-safe collection in real impl, simplified here for script
        java.util.concurrent.ConcurrentLinkedQueue<Throwable> errorQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

        // Act: Execute concurrent uploads
        long startNanos = System.nanoTime();

        for (int i = 0; i < ACTIVE_USERS; i++) {
            final String insuredId = "insured_" + i;
            final String uploadId = "upload_" + i;
            final InputStream payloadStream = createMockPayload(UPLOAD_SIZE_BYTES);

            executor.submit(() -> {
                try {
                    // Simulate portal upload flow
                    Map<String, Object> payload = Map.of(
                            "id", insuredId,
                            "uploadId", uploadId,
                            "channel", UPLOAD_CHANNEL,
                            "contentLength", UPLOAD_SIZE_BYTES
                    );

                    insuredPortalService.handleUpload(
                            insuredId,
                            uploadId,
                            payloadStream,
                            payload,
                            BACKEND_STATE,
                            UPLOAD_CHANNEL
                    );

                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    errorQueue.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(45, TimeUnit.SECONDS);
        long endNanos = System.nanoTime();
        executor.shutdown();

        // Assert: Stability and Integrity
        assertTrue(completed, "All concurrent uploads must complete within timeout.");
        assertTrue(errorQueue.isEmpty(), "No race conditions, lock contention, or timeouts detected. Errors: " + errorQueue);
        assertEquals(ACTIVE_USERS, successCount.get(), "All active users must successfully complete uploads.");

        // Assert: Infrastructure I/O Contracts
        verify(s3Service, times(ACTIVE_USERS)).uploadFile(
                eq("Secure_Storage-bucket"),
                startsWith("Secure_Storage/"),
                any(InputStream.class)
        );
        
        verify(dynamoDbService, times(ACTIVE_USERS)).putItem(
                eq("Central_Data_Store_table"),
                argThat(item -> item.containsKey("id") && item.containsKey("payload"))
        );
        
        verify(dynamoDbService, times(ACTIVE_USERS)).updateItem(
                eq("Audit_Diary_Manager_table"),
                anyString(), // partition key
                anyString(), // sort key
                anyMap()     // update expression
        );

        verify(transformValidateService, times(ACTIVE_USERS)).validatePayload(anyMap());
        verify(auditService, times(ACTIVE_USERS)).logEvent(any());

        // Assert: Performance Metrics
        double durationSec = (endNanos - startNanos) / 1_000_000_000.0;
        double throughput = ACTIVE_USERS / durationSec;

        assertTrue(durationSec < LATENCY_SLA_SECONDS, 
                "Latency SLA violated. Duration: " + durationSec + "s, SLA: " + LATENCY_SLA_SECONDS + "s");
        assertTrue(throughput > THROUGHPUT_SLA_PER_SEC, 
                "Throughput SLA violated. Throughput: " + throughput + "/s, SLA: " + THROUGHPUT_SLA_PER_SEC + "/s");
        
        System.out.printf("Performance Result: %d uploads in %.2fs (%.2f req/s)%n", 
                ACTIVE_USERS, durationSec, throughput);
    }

    private InputStream createMockPayload(int sizeBytes) {
        byte[] data = new byte[sizeBytes];
        // Fill with pseudo-random data to simulate file content
        for (int i = 0; i < sizeBytes; i++) {
            data[i] = (byte) (i % 256);
        }
        return new ByteArrayInputStream(data);
    }
}
