package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AobSubmittedAfterInitialFnolTest {

    @Mock
    private ClaimDataStoreClient mockDataStore;

    @Mock
    private DocumentManagementClient mockDocumentStore;

    private ClaimStateTransitionOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimStateTransitionOrchestrationService(mockDataStore, mockDocumentStore);
    }

    @Test
    void aob_submitted_after_initial_fnol() {
        // Arrange
        String claimId = "CLM-1001";
        String targetState = "AOB_SUBMITTED";
        Map<String, Object> initialClaimData = new HashMap<>();
        initialClaimData.put("id", claimId);
        initialClaimData.put("state", "INITIAL_FNOL");
        initialClaimData.put("payload", new HashMap<>());

        Map<String, Object> aobPayload = new HashMap<>();
        aobPayload.put("type", "AOB");
        aobPayload.put("status", "SUBMITTED");
        aobPayload.put("submittedBy", "Provider");

        when(mockDataStore.getItem(anyString(), eq("pk"))).thenReturn(initialClaimData);
        when(mockDocumentStore.uploadDocument(anyString(), anyString())).thenReturn("s3://Document Management-bucket/claims/CLM-1001/aob.json");

        // Act
        Map<String, Object> result = orchestrationService.processStateTransition(claimId, aobPayload);

        // Assert - Core State Transition & Data Model
        assertNotNull(result, "Orchestration must return a result payload");
        assertEquals(targetState, result.get("state"), "State must transition to AOB_SUBMITTED");
        assertEquals(claimId, result.get("id"), "Claim ID must be preserved");
        assertTrue(result.containsKey("payload"), "Result must contain standardized payload");

        // Assert - DynamoDB Infra Contract
        ArgumentCaptor<Map<String, Object>> itemCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockDataStore, times(1)).putItem(eq("Claim Data Store_table"), itemCaptor.capture());
        Map<String, Object> persistedItem = itemCaptor.getValue();
        assertEquals(targetState, persistedItem.get("state"));
        assertNotNull(persistedItem.get("payload"), "DynamoDB item must contain payload map");

        // Assert - S3 Infra Contract
        verify(mockDocumentStore, times(1)).uploadDocument(eq("Document Management-bucket"), anyString());

        // Assert - Input Validation & Security NFR
        assertDoesNotThrow(() -> orchestrationService.processStateTransition(null, new HashMap<>()),
                "Service must handle null/invalid inputs gracefully without leaking stack traces");
    }

    @Test
    void aob_submitted_after_initial_fnol_thread_safety() {
        ExecutorService executor = Executors.newFixedThreadPool(16);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < 100; i++) {
            String claimId = "CLM-CONC-" + i;
            Map<String, Object> payload = Map.of("type", "AOB", "status", "SUBMITTED");
            CompletableFuture.runAsync(() -> {
                try {
                    orchestrationService.processStateTransition(claimId, payload);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }, executor);
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "All concurrent tasks must complete within timeout");

        assertEquals(100, successCount.get(), "All concurrent submissions must succeed");
        assertEquals(0, errorCount.get(), "No thread-safety violations or race conditions detected");
    }
}
