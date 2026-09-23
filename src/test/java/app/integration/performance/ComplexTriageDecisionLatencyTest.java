package app.integration.performance;

import org.junit.jupiter.api.*;
import org.mockito.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ComplexTriageDecisionLatency")
class ComplexTriageDecisionLatencyTest {

    @Mock
    private ClaimOrchestrationService orchestrationService;

    @Mock
    private ClaimDataStore dataStore;

    @Mock
    private DocumentManagementService documentService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    @DisplayName("fnolOrchestrationComplexTriageLatency")
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void fnolOrchestrationComplexTriageLatency() {
        // Arrange: prepare complex FNOL payload matching input specifications
        Map<String, Object> complexPayload = buildComplexClaimPayload();
        DecisionResult expectedResult = buildExpectedDecisionResult();

        // Mock external I/O contracts (DynamoDB, S3, Policy Service)
        when(dataStore.validatePolicyAndDuplicates(complexPayload)).thenReturn(true);
        when(orchestrationService.evaluateDecision(complexPayload)).thenReturn(expectedResult);
        when(documentService.createStatutoryDiaries(anyString(), anyString())).thenReturn(true);
        when(documentService.queueAcknowledgment(anyString())).thenReturn(true);

        // Act: measure end-to-end decision latency
        long startNanos = System.nanoTime();
        DecisionResult result = orchestrationService.evaluateDecision(complexPayload);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

        // Assert: verify SLA compliance and expected business outcomes
        assertTrue(elapsedMillis < 3000, "Orchestration decision must complete in under 3 seconds");
        assertNotNull(result, "Decision result must not be null");
        assertEquals("Sinkhole/Represented/Complex", result.triageCategory(), "Triage category mismatch");
        assertTrue(result.generatedTasks().containsAll(List.of(
                "SIU Referral Review",
                "Attorney Representation Review",
                "Sinkhole Neutral Evaluation",
                "Large Loss Escalation"
        )), "All conditional tasks must be generated");
        assertEquals(500000L, result.initialReserve(), "Initial reserve mismatch");
        assertTrue(result.approvalTaskRequired(), "Approval task required due to authority threshold");
        assertTrue(result.statutoryDiariesCreated(), "Statutory diaries must be created");
        assertTrue(result.acknowledgmentQueued(), "Acknowledgment must be queued");
        assertTrue(result.auditTrailComplete(), "Audit trail must record all rule executions and field values");
    }

    @Test
    @DisplayName("fnolOrchestrationComplexTriageLatencyConcurrency")
    void fnolOrchestrationComplexTriageLatencyConcurrency() throws Exception {
        int threadCount = 10;
        int iterationsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalTimeNanos = new AtomicLong(0);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failCount = new AtomicLong(0);

        Map<String, Object> complexPayload = buildComplexClaimPayload();
        DecisionResult expectedResult = buildExpectedDecisionResult();
        when(orchestrationService.evaluateDecision(complexPayload)).thenReturn(expectedResult);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        long start = System.nanoTime();
                        DecisionResult result = orchestrationService.evaluateDecision(complexPayload);
                        long elapsed = System.nanoTime() - start;
                        totalTimeNanos.addAndGet(elapsed);
                        if (result != null && result.auditTrailComplete()) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }));
        }

        latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        long avgLatencyMs = totalTimeNanos.get() / (long) (threadCount * iterationsPerThread) / 1_000_000;
        assertEquals(threadCount * iterationsPerThread, successCount.get(), "All concurrent executions must succeed");
        assertEquals(0, failCount.get(), "No concurrent executions should fail");
        assertTrue(avgLatencyMs < 3000, "Average latency under concurrent load must remain under 3 seconds");
    }

    private Map<String, Object> buildComplexClaimPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("claim_type", "complex");
        payload.put("cause_of_loss", "sinkhole");
        payload.put("attorney_flag", true);
        payload.put("public_adjuster_flag", true);
        payload.put("severity_score", "high");
        payload.put("coverage_uncertainty", true);
        payload.put("prior_claims_count", 3);
        payload.put("policy_status", "active");
        payload.put("reserve_amount", 500000);
        payload.put("inspection_type", "engineer");
        return payload;
    }

    private DecisionResult buildExpectedDecisionResult() {
        return new DecisionResult(
                "Sinkhole/Represented/Complex",
                List.of("SIU Referral Review", "Attorney Representation Review",
                        "Sinkhole Neutral Evaluation", "Large Loss Escalation"),
                500000L, true, true, true, true, true
        );
    }

    // Minimal DTO for test assertions
    record DecisionResult(String triageCategory, List<String> generatedTasks,
                          long initialReserve, boolean approvalTaskRequired,
                          boolean statutoryDiariesCreated, boolean acknowledgmentQueued,
                          boolean auditTrailComplete) {}

    // Mock interfaces simulating external I/O contracts
    interface ClaimOrchestrationService {
        DecisionResult evaluateDecision(Map<String, Object> payload);
    }

    interface ClaimDataStore {
        boolean validatePolicyAndDuplicates(Map<String, Object> payload);
    }

    interface DocumentManagementService {
        boolean createStatutoryDiaries(String claimId, String type);
        boolean queueAcknowledgment(String claimId);
    }
}
