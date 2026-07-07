package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultipleSpecialistsAccessSameTaskConcurrently {

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    private ClaimEnrichmentService claimEnrichmentService;

    @BeforeEach
    void setUp() {
        claimEnrichmentService = new ClaimEnrichmentService(workflowTaskRouter, rulesEngineDecisionService, auditDiaryStore);
    }

    @Test
    void multiple_specialists_access_same_task_concurrently() throws Exception {
        String taskId = "task-456";
        Map<String, Object> initialPayload = Map.of("id", taskId, "status", "ASSIGNED", "specialist", "SPEC_A");
        Map<String, Object> enrichedPayload = Map.of("id", taskId, "status", "ENRICHED", "score", 92.0, "riskLevel", "LOW");

        when(workflowTaskRouter.fetchTaskById(taskId)).thenReturn(initialPayload);
        when(rulesEngineDecisionService.evaluate(any(Map.class))).thenReturn(enrichedPayload);
        doNothing().when(auditDiaryStore).write(anyString(), anyString());

        int concurrentSpecialists = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentSpecialists);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentSpecialists);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentSpecialists; i++) {
            final int specialistId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Map<String, Object> result = claimEnrichmentService.process(taskId, specialistId);
                    assertNotNull(result, "Result payload must not be null");
                    assertEquals("ENRICHED", result.get("status"), "Task status must transition to ENRICHED");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = completionLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All concurrent specialist requests must complete within timeout");
        assertEquals(concurrentSpecialists, successCount.get(), "All concurrent accesses must succeed without data corruption");
        assertEquals(0, failureCount.get(), "No thread-safety violations or race conditions should occur");
        verify(rulesEngineDecisionService, times(concurrentSpecialists)).evaluate(any(Map.class));
    }
}
