package app.integration.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Performance test for Claim Initiation & Routing:calculation:transformation.
 * Measures transformation latency for complex FNOL scenarios with multiple policy matches,
 * duplicate checks, and reserve calculations. Verifies thread safety and structured logging.
 */
@DisplayName("FnoLComplexTransformLatency")
class FnoLComplexTransformLatency {

    private static final int POLICY_MATCH_COUNT = 5;
    private static final long MAX_LATENCY_MS = 3000;
    private static final int THREAD_COUNT = 10;
    private static final String FEATURE_ID = "claim_data_standardization_transformation_valida";

    private PolicyValidationService policyValidationService;
    private RulesEngineService rulesEngineService;
    private DocumentStoreService documentStoreService;
    private ClaimTransformationService transformationService;
    private List<LogRecord> capturedLogs;

    @BeforeEach
    void setUp() {
        policyValidationService = mock(PolicyValidationService.class);
        rulesEngineService = mock(RulesEngineService.class);
        documentStoreService = mock(DocumentStoreService.class);

        configureComplexScenarioMocks();

        transformationService = new ClaimTransformationService(
            policyValidationService,
            rulesEngineService,
            documentStoreService
        );

        capturedLogs = new ArrayList<>();
        setupLoggingCapture();
    }

    private void configureComplexScenarioMocks() {
        Map<String, Object> policyMatches = new HashMap<>();
        policyMatches.put("matches", new ArrayList<>(List.of("policy_1", "policy_2", "policy_3", "policy_4", "policy_5")));
        when(policyValidationService.getPolicyMatches(anyString())).thenReturn(policyMatches);

        Map<String, Object> rulesResult = new HashMap<>();
        rulesResult.put("siuIndicators", true);
        rulesResult.put("reserveAmount", 15000.0);
        rulesResult.put("routingDecision", "ROUTING_DECISION_ACCURATE");
        rulesResult.put("duplicateTaskCreated", true);
        when(rulesEngineService.evaluateRules(anyMap())).thenReturn(rulesResult);

        when(documentStoreService.storeDocument(anyString(), anyString(), anyMap())).thenReturn("s3://DocumentStoreService-bucket/claim_data_standardization_transformation_valida_123.json");
    }

    private void setupLoggingCapture() {
        Logger logger = Logger.getLogger(ClaimTransformationService.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                capturedLogs.add(record);
            }

            @Override
            public void flush() {
                // No-op
            }

            @Override
            public void close() throws SecurityException {
                // No-op
            }
        };
        logger.addHandler(handler);
        logger.setLevel(java.util.logging.Level.ALL);
    }

    @Test
    @DisplayName("fnol_complex_policy_match_duplicate_latency")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void fnol_complex_policy_match_duplicate_latency() throws Exception {
        Map<String, Object> payload = createComplexPayload();

        long startTime = System.nanoTime();
        TransformationResult result = transformationService.transform(payload);
        long endTime = System.nanoTime();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        assertTrue(durationMs < MAX_LATENCY_MS,
            String.format("Transformation latency %dms exceeds threshold %dms", durationMs, MAX_LATENCY_MS));

        assertEquals("ROUTING_DECISION_ACCURATE", result.getRoutingDecision());
        assertTrue(result.isDuplicateTaskCreated());
        assertEquals(15000.0, result.getReserveAmount(), 0.001);
        assertEquals("s3://DocumentStoreService-bucket/claim_data_standardization_transformation_valida_123.json", result.getDocumentUri());

        verify(policyValidationService, times(POLICY_MATCH_COUNT)).getPolicyMatches(anyString());
        verify(rulesEngineService).evaluateRules(anyMap());
        verify(documentStoreService).storeDocument(anyString(), anyString(), anyMap());

        assertStructuredLogging();
        verifyThreadSafety();
    }

    private void assertStructuredLogging() {
        List<String> logMessages = capturedLogs.stream()
            .map(LogRecord::getMessage)
            .collect(Collectors.toList());

        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("feature=Claim Initiation & Routing")),
            "Missing feature context in structured logs");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains("calculation=transformation")),
            "Missing calculation context in structured logs");
        assertTrue(logMessages.stream().anyMatch(msg -> msg.contains(FEATURE_ID)),
            "Missing entity ID in structured logs");
    }

    private void verifyThreadSafety() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    transformationService.transform(createComplexPayload());
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Thread safety test did not complete within timeout");
        assertNull(error.get(), "Thread safety violation detected: " + error.get().getMessage());
    }

    private Map<String, Object> createComplexPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "claim_123");
        payload.put("payload_type", "complex_multi_match");
        payload.put("duplicate_check", true);
        payload.put("siu_indicators", true);
        payload.put("reserve_calculation", true);
        payload.put("severityScore", 85);
        return payload;
    }

    // Minimal stub interfaces to mock external I/O contracts
    static interface PolicyValidationService {
        Map<String, Object> getPolicyMatches(String claimId);
    }

    static interface RulesEngineService {
        Map<String, Object> evaluateRules(Map<String, Object> payload);
    }

    static interface DocumentStoreService {
        String storeDocument(String bucketName, String objectKeyPattern, Map<String, Object> data);
    }

    static class ClaimTransformationService {
        private final PolicyValidationService policyValidationService;
        private final RulesEngineService rulesEngineService;
        private final DocumentStoreService documentStoreService;
        private static final Logger LOG = Logger.getLogger(ClaimTransformationService.class.getName());

        ClaimTransformationService(PolicyValidationService policyValidationService,
                                   RulesEngineService rulesEngineService,
                                   DocumentStoreService documentStoreService) {
            this.policyValidationService = policyValidationService;
            this.rulesEngineService = rulesEngineService;
            this.documentStoreService = documentStoreService;
        }

        TransformationResult transform(Map<String, Object> payload) {
            LOG.info(() -> String.format("feature=Claim Initiation & Routing calculation=transformation entity=%s start", payload.get("id")));

            String claimId = (String) payload.get("id");
            Map<String, Object> policyMatches = policyValidationService.getPolicyMatches(claimId);
            
            int matchCount = ((List<?>) policyMatches.get("matches")).size();
            for (int i = 0; i < matchCount; i++) {
                // Simulate policy match processing
                Thread.sleep(1); 
            }

            Map<String, Object> rulesResult = rulesEngineService.evaluateRules(payload);
            String routingDecision = (String) rulesResult.get("routingDecision");
            Double reserveAmount = (Double) rulesResult.get("reserveAmount");
            Boolean duplicateTaskCreated = (Boolean) rulesResult.get("duplicateTaskCreated");

            String documentUri = documentStoreService.storeDocument(
                "DocumentStoreService-bucket",
                String.format("DocumentStoreService/%s.json", claimId),
                payload
            );

            LOG.info(() -> String.format("feature=Claim Initiation & Routing calculation=transformation entity=%s result=%s", claimId, routingDecision));

            return new TransformationResult(routingDecision, duplicateTaskCreated, reserveAmount, documentUri);
        }
    }

    static class TransformationResult {
        private final String routingDecision;
        private final boolean duplicateTaskCreated;
        private final double reserveAmount;
        private final String documentUri;

        TransformationResult(String routingDecision, boolean duplicateTaskCreated, double reserveAmount, String documentUri) {
            this.routingDecision = routingDecision;
            this.duplicateTaskCreated = duplicateTaskCreated;
            this.reserveAmount = reserveAmount;
            this.documentUri = documentUri;
        }

        String getRoutingDecision() { return routingDecision; }
        boolean isDuplicateTaskCreated() { return duplicateTaskCreated; }
        double getReserveAmount() { return reserveAmount; }
        String getDocumentUri() { return documentUri; }
    }
}
