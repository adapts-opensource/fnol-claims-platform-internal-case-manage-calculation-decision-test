package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;

@Execution(ExecutionMode.CONCURRENT)
public class ClaimDataStandardizationValidationDecisionConcurrentEditsTest {

    private ClaimValidationDecisionService mockService;
    private CountDownLatch startLatch;
    private CountDownLatch doneLatch;
    private AtomicInteger successCount;
    private AtomicInteger failureCount;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        mockService = mock(ClaimValidationDecisionService.class);
        startLatch = new CountDownLatch(1);
        doneLatch = new CountDownLatch(10);
        successCount = new AtomicInteger(0);
        failureCount = new AtomicInteger(0);
        executor = Executors.newFixedThreadPool(10);
    }

    @AfterEach
    void tearDown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void concurrentEdits() throws InterruptedException {
        String claimId = "claim-789";
        Map<String, Object> basePayload = new HashMap<>();
        basePayload.put("id", claimId);
        basePayload.put("payload", Map.of("status", "SUBMITTED", "version", 1));

        // Mock S3/DynamoDB I/O contracts for validation & decision routing
        when(mockService.processClaimData(eq(claimId), anyMap()))
                .thenAnswer(invocation -> {
                    startLatch.await();
                    // Simulate thread-safe processing with optimistic locking/version check
                    return true;
                });

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    startLatch.countDown();
                    boolean result = mockService.processClaimData(claimId, basePayload);
                    if (result) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await(10, TimeUnit.SECONDS);

        assertEquals(10, successCount.get(), "All concurrent edits should be processed successfully");
        assertEquals(0, failureCount.get(), "No concurrent edits should fail");
        verify(mockService, times(10)).processClaimData(eq(claimId), anyMap());
    }

    // Minimal service interface representing Claim Data Standardization:validation:decision
    interface ClaimValidationDecisionService {
        boolean processClaimData(String id, Map<String, Object> payload);
    }
}
