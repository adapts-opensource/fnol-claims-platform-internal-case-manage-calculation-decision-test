package app.integration.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Performance test for Claim Data Standardization:calculation:transformation.
 * Measures response time and throughput under nominal concurrent load.
 */
@ExtendWith(MockitoExtension.class)
public class MeasureResponseTimeForClaimDataStandardizationCalculation {

    @Mock
    private AuditDiaryStore mockAuditDiaryStore;
    @Mock
    private RulesEngineDecisionService mockRulesEngine;
    @Mock
    private WorkflowTaskRouter mockTaskRouter;

    private ClaimDataTransformationService transformationService;

    private static final int NOMINAL_LOAD_ITERATIONS = 100;
    private static final long MAX_AVG_RESPONSE_TIME_MS = 500;
    private static final long MIN_THROUGHPUT_RPS = 50;

    @BeforeEach
    void setUp() {
        transformationService = new ClaimDataTransformationService(
                mockAuditDiaryStore, mockRulesEngine, mockTaskRouter);
    }

    @Test
    void measure_response_time_for_claim_data_standardization_calculation_transformation_under_nominal_load() throws Exception {
        AtomicLong totalNanoTime = new AtomicLong(0);
        int iterations = NOMINAL_LOAD_ITERATIONS;

        long startNanos = System.nanoTime();
        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < iterations; i++) {
                final String id = UUID.randomUUID().toString();
                final Map<String, Object> payload = createNominalPayload();
                executor.submit(() -> {
                    long iterationStart = System.nanoTime();
                    try {
                        transformationService.transform(id, payload);
                    } finally {
                        totalNanoTime.addAndGet(System.nanoTime() - iterationStart);
                    }
                });
            }
        }
        long endNanos = System.nanoTime();

        long totalTimeMs = TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos);
        long avgResponseTimeMs = TimeUnit.NANOSECONDS.toMillis(totalNanoTime.get()) / iterations;
        double throughputRps = iterations / (totalTimeMs / 1000.0);

        assertTrue(avgResponseTimeMs <= MAX_AVG_RESPONSE_TIME_MS,
                "Average response time " + avgResponseTimeMs + "ms exceeds threshold " + MAX_AVG_RESPONSE_TIME_MS + "ms");
        assertTrue(throughputRps >= MIN_THROUGHPUT_RPS,
                "Throughput " + String.format("%.2f", throughputRps) + " RPS is below threshold " + MIN_THROUGHPUT_RPS + " RPS");

        verify(mockAuditDiaryStore, times(iterations)).store(anyString(), anyString());
        verify(mockRulesEngine, times(iterations)).evaluate(anyString(), anyMap());
        verify(mockTaskRouter, times(iterations)).route(anyString(), anyMap());
    }

    private Map<String, Object> createNominalPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("claimId", "CLM-2023-001");
        payload.put("amount", 1500.00);
        payload.put("status", "SUBMITTED");
        return payload;
    }

    // Internal service representing the transformation feature logic
    static class ClaimDataTransformationService {
        private final AuditDiaryStore auditDiaryStore;
        private final RulesEngineDecisionService rulesEngine;
        private final WorkflowTaskRouter taskRouter;

        ClaimDataTransformationService(AuditDiaryStore auditDiaryStore, RulesEngineDecisionService rulesEngine, WorkflowTaskRouter taskRouter) {
            this.auditDiaryStore = auditDiaryStore;
            this.rulesEngine = rulesEngine;
            this.taskRouter = taskRouter;
        }

        void transform(String id, Map<String, Object> payload) {
            // Simulate CPU-bound standardization transformation
            String normalizedId = id.toUpperCase().replace("-", "");
            Map<String, Object> standardized = new HashMap<>();
            standardized.put("id", normalizedId);
            standardized.put("payload", payload);
            standardized.put("processedAt", System.currentTimeMillis());

            // Mock I/O contracts as per infra_io_contracts
            auditDiaryStore.store("AuditDiaryStore-bucket", "AuditDiaryStore/" + normalizedId + ".json");
            rulesEngine.evaluate("RulesEngineDecisionService_table", standardized);
            taskRouter.route("WorkflowTaskRouter_table", standardized);
        }
    }

    // Mock interfaces representing infra IO contracts
    interface AuditDiaryStore { void store(String bucketName, String objectKeyPattern); }
    interface RulesEngineDecisionService { void evaluate(String tableName, Map<String, Object> itemPayload); }
    interface WorkflowTaskRouter { void route(String tableName, Map<String, Object> itemPayload); }
}
