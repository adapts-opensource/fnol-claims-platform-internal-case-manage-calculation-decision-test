package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationOrchestrationMockTest {

    // Minimal domain model matching the spec
    static class ClaimDataStandardizationStateTransitionOrch {
        private String id;
        private Map<String, Object> payload;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Map<String, Object> getPayload() { return payload; }
        public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    }

    // Mocked infrastructure contracts per spec
    interface DynamoDbClient {
        void putItem(Map<String, Object> item);
    }

    interface S3Client {
        void putObject(String bucketName, String objectKeyPattern, byte[] data);
    }

    // Orchestrator under test
    static class ClaimDataStandardizationOrchestrator {
        private final DynamoDbClient dynamoDbClient;
        private final S3Client s3Client;

        ClaimDataStandardizationOrchestrator(DynamoDbClient dynamoDbClient, S3Client s3Client) {
            this.dynamoDbClient = dynamoDbClient;
            this.s3Client = s3Client;
        }

        void processClaim(ClaimDataStandardizationStateTransitionOrch item) {
            // Standardization & State Transition logic
            Map<String, Object> standardized = new HashMap<>(item.getPayload());
            standardized.put("standardizedAt", System.currentTimeMillis());
            standardized.put("status", "PROCESSED");

            // Persist to Claim Data Store
            dynamoDbClient.putItem(standardized);
            // Store document in Document Management
            s3Client.putObject("Document Management-bucket", item.getId() + ".json", "payload-data".getBytes());
        }
    }

    @Mock
    private DynamoDbClient mockDynamoDb;

    @Mock
    private S3Client mockS3;

    private ClaimDataStandardizationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimDataStandardizationOrchestrator(mockDynamoDb, mockS3);
    }

    @Test
    void high_volume_cat_surge() throws Exception {
        // Simulate high-volume CAT surge with 1000 concurrent claim standardization requests
        int volume = 1000;
        List<ClaimDataStandardizationStateTransitionOrch> batch = new ArrayList<>(volume);
        for (int i = 0; i < volume; i++) {
            ClaimDataStandardizationStateTransitionOrch item = new ClaimDataStandardizationStateTransitionOrch();
            item.setId(UUID.randomUUID().toString());
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventDate", "2023-10-01");
            payload.put("severity", "HIGH");
            payload.put("claimType", "AUTO");
            item.setPayload(payload);
            batch.add(item);
        }

        ExecutorService executor = Executors.newFixedThreadPool(16);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Concurrently process batch to simulate thread-safety & HA multi-AZ load
        for (ClaimDataStandardizationStateTransitionOrch item : batch) {
            executor.submit(() -> {
                try {
                    orchestrator.processClaim(item);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        boolean completed = executor.awaitTermination(30, TimeUnit.SECONDS);
        assertTrue(completed, "All high-volume tasks must complete within timeout");

        // Verify thread-safe execution and mock isolation
        assertEquals(volume, successCount.get(), "All high-volume CAT surge items must be processed successfully");
        assertEquals(0, failureCount.get(), "No failures expected under mocked conditions");

        // Verify infra I/O contracts are invoked exactly as expected
        verify(mockDynamoDb, times(volume)).putItem(any(Map.class));
        verify(mockS3, times(volume)).putObject(any(String.class), any(String.class), any(byte[].class));
    }
}
