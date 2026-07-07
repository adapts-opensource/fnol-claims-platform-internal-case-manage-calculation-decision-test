package app.integration.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Claim Data Standardization Decision Transformation Performance Tests")
class ClaimDataStandardizationDecisionTransformationPerformanceTest {

    private ClaimDataStandardizationDecisionService decisionService;

    @BeforeEach
    void setUp() {
        // Mock external I/O contracts: S3 AuditDiaryStore, DynamoDB RulesEngineDecisionService & WorkflowTaskRouter
        decisionService = new ClaimDataStandardizationDecisionService(
            new MockAuditDiaryStore(),
            new MockRulesEngineDecisionService(),
            new MockWorkflowTaskRouter()
        );
    }

    @Test
    @DisplayName("measure_response_time_for_claim_data_standardization_decision_transformation_under_nominal_load")
    void measure_response_time_for_claim_data_standardization_decision_transformation_under_nominal_load() {
        final int NOMINAL_LOAD_ITERATIONS = 500;
        final long MAX_AVG_RESPONSE_TIME_MS = 100;
        final AtomicLong totalTimeNs = new AtomicLong(0);

        for (int i = 0; i < NOMINAL_LOAD_ITERATIONS; i++) {
            String claimId = UUID.randomUUID().toString();
            Map<String, Object> payload = Map.of("claimType", "FNOL", "severity", "LOW", "status", "OPEN");

            long start = System.nanoTime();
            Map<String, Object> result = decisionService.transform(claimId, payload);
            long end = System.nanoTime();

            assertNotNull(result, "Transformation must return a non-null result under nominal load");
            assertTrue(result.containsKey("id"), "Result must contain 'id' field");
            assertTrue(result.containsKey("payload"), "Result must contain 'payload' field");

            totalTimeNs.addAndGet(end - start);
        }

        long avgResponseTimeNs = totalTimeNs.get() / NOMINAL_LOAD_ITERATIONS;
        long avgResponseTimeMs = TimeUnit.NANOSECONDS.toMillis(avgResponseTimeNs);
        double throughput = NOMINAL_LOAD_ITERATIONS / (avgResponseTimeMs / 1000.0);

        System.out.printf("Nominal Load Performance: Avg Response Time: %d ms, Throughput: %.2f req/s%n", avgResponseTimeMs, throughput);

        assertLessThan(avgResponseTimeMs, MAX_AVG_RESPONSE_TIME_MS,
            String.format("Average response time %d ms exceeds nominal threshold %d ms", avgResponseTimeMs, MAX_AVG_RESPONSE_TIME_MS));
    }

    // Service under test with mocked infra contracts
    private static class ClaimDataStandardizationDecisionService {
        private final MockAuditDiaryStore auditDiaryStore;
        private final MockRulesEngineDecisionService rulesEngineDecisionService;
        private final MockWorkflowTaskRouter workflowTaskRouter;

        ClaimDataStandardizationDecisionService(MockAuditDiaryStore auditDiaryStore,
                                                MockRulesEngineDecisionService rulesEngineDecisionService,
                                                MockWorkflowTaskRouter workflowTaskRouter) {
            this.auditDiaryStore = auditDiaryStore;
            this.rulesEngineDecisionService = rulesEngineDecisionService;
            this.workflowTaskRouter = workflowTaskRouter;
        }

        Map<String, Object> transform(String id, Map<String, Object> payload) {
            // Simulate nominal transformation logic + minor I/O latency
            try {
                Thread.sleep(10); // ~10ms nominal processing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Mock S3 write
            auditDiaryStore.write("AuditDiaryStore-bucket", "AuditDiaryStore/" + id + ".json", payload);
            // Mock DynamoDB reads/writes
            rulesEngineDecisionService.query("RulesEngineDecisionService_table", "pk");
            workflowTaskRouter.route("WorkflowTaskRouter_table", "pk", payload);

            Map<String, Object> standardizedResult = new HashMap<>();
            standardizedResult.put("id", id);
            standardizedResult.put("payload", payload);
            return standardizedResult;
        }
    }

    // Mocked Infra I/O Contracts
    private static class MockAuditDiaryStore {
        void write(String bucketName, String objectKeyPattern, Map<String, Object> data) {
            // No-op mock for S3 AuditDiaryStore
        }
    }

    private static class MockRulesEngineDecisionService {
        Map<String, Object> query(String tableName, String partitionKey) {
            // No-op mock for DynamoDB RulesEngineDecisionService
            return Map.of();
        }
    }

    private static class MockWorkflowTaskRouter {
        void route(String tableName, String partitionKey, Map<String, Object> itemPayload) {
            // No-op mock for DynamoDB WorkflowTaskRouter
        }
    }
}
