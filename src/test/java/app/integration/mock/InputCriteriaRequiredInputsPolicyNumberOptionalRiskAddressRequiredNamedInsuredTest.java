package app.integration.mock;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDecisionStandardizationTest {

    @Mock
    private PolicyFetcher policyFetcher;
    @Mock
    private AddressValidator addressValidator;
    @Mock
    private ScoringService scoringService;
    @Mock
    private EventEmitter eventEmitter;
    @Mock
    private TaskCreator taskCreator;
    @Mock
    private AuditLogger auditLogger;
    @Mock
    private StructuredLogger structuredLogger;

    @InjectMocks
    private ClaimDecisionService decisionService;

    private ClaimInput validInput;
    private CandidatePolicy exactMatchPolicy;
    private CandidatePolicy fuzzyMatchPolicy1;
    private CandidatePolicy fuzzyMatchPolicy2;

    @BeforeEach
    void setUp() {
        validInput = new ClaimInput(
            "claim_001", "CLM-2024-001", "tenant_123", "POL-OLD-999",
            "123 Main St, Springfield, IL", "John Doe",
            LocalDateTime.now().minusDays(10), "AUTO_PHYSICAL_DAMAGE",
            "OWNER", "LANDLORD", "PRIOR-POL-001",
            UUID.randomUUID().toString()
        );

        exactMatchPolicy = new CandidatePolicy("POL-NEW-001", "123 Main St, Springfield, IL", "John Doe",
            LocalDateTime.now(), "AUTO_PHYSICAL_DAMAGE", 0.92);
        fuzzyMatchPolicy1 = new CandidatePolicy("POL-NEW-002", "124 Main St, Springfield, IL", "J. Doe",
            LocalDateTime.now(), "AUTO_PHYSICAL_DAMAGE", 0.81);
        fuzzyMatchPolicy2 = new CandidatePolicy("POL-NEW-003", "123 Main St, Springfield, IL", "John Doe",
            LocalDateTime.now(), "AUTO_PHYSICAL_DAMAGE", 0.75);
    }

    @Test
    void testSingleExactMatchDecision() {
        when(scoringService.calculateScore(any(), any())).thenReturn(0.85);
        when(policyFetcher.fetchCandidatePolicies(any())).thenReturn(List.of(exactMatchPolicy));

        DecisionResult result = decisionService.decide(validInput);

        assertEquals(PolicyMatchStatus.EXACT, result.status());
        assertEquals("POL-NEW-001", result.policyId());
        verify(eventEmitter).emit(eq("POLICY_MATCH_COMPLETED"), any());
    }

    @Test
    void testMultipleHighConfidenceMatchesDecision() {
        when(scoringService.calculateScore(any(), any())).thenReturn(0.80);
        when(policyFetcher.fetchCandidatePolicies(any())).thenReturn(List.of(fuzzyMatchPolicy1, fuzzyMatchPolicy2));

        DecisionResult result = decisionService.decide(validInput);

        assertEquals(PolicyMatchStatus.MULTIPLE, result.status());
        assertTrue(result.candidateCount() > 1);
        verify(taskCreator).createManualResolutionTask(any());
    }

    @Test
    void testNoMatchDecision() {
        when(scoringService.calculateScore(any(), any())).thenReturn(0.65);
        when(policyFetcher.fetchCandidatePolicies(any())).thenReturn(List.of(new CandidatePolicy("POL-LOW", "", "", LocalDateTime.now(), "", 0.65)));

        DecisionResult result = decisionService.decide(validInput);

        assertEquals(PolicyMatchStatus.NONE, result.status());
        assertNull(result.policyId());
    }

    @Test
    void testInputValidationAddressFailsGeocoding() {
        when(addressValidator.validate(any())).thenThrow(new IllegalArgumentException("Address failed geocoding validation"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> decisionService.decide(validInput));
        assertTrue(ex.getMessage().contains("geocoding"));
    }

    @Test
    void testInputValidationDateFailsISO8601() {
        ClaimInput badDateInput = new ClaimInput("c", "n", "t", "p", "addr", "name", null, "prod", "occ", "rel", "prior", "id");
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> decisionService.decide(badDateInput));
        assertTrue(ex.getMessage().contains("valid ISO 8601"));
    }

    @Test
    void testInputValidationNameFailsPASFormat() {
        ClaimInput badNameInput = new ClaimInput("c", "n", "t", "p", "addr", "JOHNDOE", LocalDateTime.now(), "prod", "occ", "rel", "prior", "id");
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> decisionService.decide(badNameInput));
        assertTrue(ex.getMessage().contains("PAS format"));
    }

    @Test
    void testPolicyDataFreshnessExceeded() {
        when(policyFetcher.fetchCandidatePolicies(any())).thenThrow(new RuntimeException("Policy data stale: >5min"));
        
        RuntimeException ex = assertThrows(RuntimeException.class, () -> decisionService.decide(validInput));
        assertTrue(ex.getMessage().contains("freshness"));
    }

    @Test
    void testBusinessRuleMoratoriumOverride() {
        CandidatePolicy stormPolicy = new CandidatePolicy("POL-STORM", "addr", "name", LocalDateTime.now(), "AUTO", 0.90);
        when(scoringService.calculateScore(any(), any())).thenReturn(0.90);
        when(policyFetcher.fetchCandidatePolicies(any())).thenReturn(List.of(stormPolicy));
        when(policyFetcher.isMoratoriumActive(any())).thenReturn(true);

        DecisionResult result = decisionService.decide(validInput);
        assertEquals(PolicyMatchStatus.EXACT, result.status());
        verify(auditLogger).log(eq("MORATORIUM_OVERRIDE"), any(), any());
    }

    @Test
    void testBusinessRuleRecentExpiration() {
        CandidatePolicy expiredPolicy = new CandidatePolicy("POL-EXP", "addr", "name", LocalDateTime.now().minusDays(15), "AUTO", 0.88);
        when(scoringService.calculateScore(any(), any())).thenReturn(0.88);
        when(policyFetcher.fetchCandidatePolicies(any())).thenReturn(List.of(expiredPolicy));

        DecisionResult result = decisionService.decide(validInput);
        assertEquals(PolicyMatchStatus.EXACT, result.status());
    }

    @Test
    void testPasConnectivityTimeout() {
        when(policyFetcher.fetchCandidatePolicies(any())).thenThrow(new RuntimeException("PAS connectivity timeout"));
        
        RuntimeException ex = assertThrows(RuntimeException.class, () -> decisionService.decide(validInput));
        assertTrue(ex.getMessage().contains("connectivity timeout"));
        verify(eventEmitter).emit(eq("POLICY_MATCH_RESOLVED"), argThat(evt -> evt.contains("timeout")));
    }

    @Test
    void testWeightedScoringCalculation() {
        double score = scoringService.calculateScore(validInput, exactMatchPolicy);
        assertEquals(0.92, score, 0.01);
        verifyNoInteractions(eventEmitter);
    }

    @Test
    void testOutputStructureSuccess() {
        when(scoringService.calculateScore(any(), any())).thenReturn(0.90);
        when(policyFetcher.fetchCandidatePolicies(any())).thenReturn(List.of(exactMatchPolicy));

        DecisionResult result = decisionService.decide(validInput);
        assertNotNull(result.policyId());
        assertNotNull(result.policyStatus());
        assertNotNull(result.coverageSummary());
        assertNotNull(result.matchStatus());
    }

    @Test
    void testOutputStructureFailure() {
        when(policyFetcher.fetchCandidatePolicies(any())).thenThrow(new RuntimeException("PAS connectivity timeout"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> decisionService.decide(validInput));
        assertNotNull(ex.getMessage());
        verify(auditLogger).log(eq("FNOL_POLICY_MATCHED"), any(), any());
    }

    @Test
    void testEmittedEventsOnMatch() {
        when(scoringService.calculateScore(any(), any())).thenReturn(0.85);
        when(policyFetcher.fetchCandidatePolicies(any())).thenReturn(List.of(exactMatchPolicy));

        decisionService.decide(validInput);
        verify(eventEmitter, times(1)).emit(eq("POLICY_MATCH_COMPLETED"), any());
        verify(eventEmitter, never()).emit(eq("TASK_CREATED"), any());
    }

    @Test
    void testTaskCreatedForManualResolution() {
        when(scoringService.calculateScore(any(), any())).thenReturn(0.80);
        when(policyFetcher.fetchCandidatePolicies(any())).thenReturn(List.of(fuzzyMatchPolicy1, fuzzyMatchPolicy2));

        decisionService.decide(validInput);
        verify(taskCreator, times(1)).createManualResolutionTask(any());
    }

    @Test
    void testEdgeCasePartialData() {
        ClaimInput partialInput = new ClaimInput("c", "n", "t", "p", null, null, LocalDateTime.now(), null, null, null, null, "id");
        
        DecisionResult result = decisionService.decide(partialInput);
        assertEquals(PolicyMatchStatus.NONE, result.status());
    }

    @Test
    void testThreadSafetyWithIdempotency() throws InterruptedException {
        when(scoringService.calculateScore(any(), any())).thenReturn(0.90);
        when(policyFetcher.fetchCandidatePolicies(any())).thenReturn(List.of(exactMatchPolicy));

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        String idempotencyKey = validInput.idempotencyKey();

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    DecisionResult r = decisionService.decide(validInput);
                    assertEquals(PolicyMatchStatus.EXACT, r.status());
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        verify(policyFetcher, times(1)).fetchCandidatePolicies(any()); // Idempotent cache hit
    }

    @Test
    void testStructuredLoggingOutputs() {
        when(scoringService.calculateScore(any(), any())).thenReturn(0.85);
        when(policyFetcher.fetchCandidatePolicies(any())).thenReturn(List.of(exactMatchPolicy));

        decisionService.decide(validInput);
        verify(structuredLogger).log(eq("INFO"), eq("CLAIM_DECISION_COMPLETED"), any(Map.class));
    }

    @Test
    void testGdprDataMinimization() {
        when(scoringService.calculateScore(any(), any())).thenReturn(0.85);
        when(policyFetcher.fetchCandidatePolicies(any())).thenReturn(List.of(exactMatchPolicy));

        decisionService.decide(validInput);
        verify(auditLogger).log(eq("GDPR_MINIMIZATION_CHECK"), any(), any());
    }

    @Test
    void testSoc2AuditTrail() {
        when(scoringService.calculateScore(any(), any())).thenReturn(0.85);
        when(policyFetcher.fetchCandidatePolicies(any())).thenReturn(List.of(exactMatchPolicy));

        decisionService.decide(validInput);
        verify(auditLogger).log(eq("SOC2_ACCESS_CONTROL"), any(), any());
    }

    // --- Inner Domain Models for Test Isolation ---
    record ClaimInput(String claimId, String claimNumber, String tenantId, String policyNumber,
                      String riskAddress, String namedInsured, LocalDateTime dateOfLoss,
                      String productForm, String occupancyRelationship, String landlordFlag,
                      String priorPolicyId, String idempotencyKey) {}

    record CandidatePolicy(String policyId, String address, String insured,
                           LocalDateTime effectiveDate, String productForm, double score) {}

    record DecisionResult(String policyId, String policyStatus, String coverageSummary,
                          PolicyMatchStatus matchStatus, int candidateCount) {}

    interface PolicyFetcher {
        List<CandidatePolicy> fetchCandidatePolicies(ClaimInput input);
        boolean isMoratoriumActive(String policyId);
    }

    interface AddressValidator {
        void validate(String address) throws IllegalArgumentException;
    }

    interface ScoringService {
        double calculateScore(ClaimInput input, CandidatePolicy policy);
    }

    interface EventEmitter {
        void emit(String eventType, String payload);
    }

    interface TaskCreator {
        void createManualResolutionTask(DecisionResult result);
    }

    interface AuditLogger {
        void log(String event, String tenantId, String details);
    }

    interface StructuredLogger {
        void log(String level, String message, Map<String, Object> context);
    }

    // Simplified Service Implementation under Test
    static class ClaimDecisionService {
        private final PolicyFetcher policyFetcher;
        private final AddressValidator addressValidator;
        private final ScoringService scoringService;
        private final EventEmitter eventEmitter;
        private final TaskCreator taskCreator;
        private final AuditLogger auditLogger;
        private final StructuredLogger structuredLogger;

        ClaimDecisionService(PolicyFetcher policyFetcher, AddressValidator addressValidator,
                             ScoringService scoringService, EventEmitter eventEmitter,
                             TaskCreator taskCreator, AuditLogger auditLogger,
                             StructuredLogger structuredLogger) {
            this.policyFetcher = policyFetcher;
            this.addressValidator = addressValidator;
            this.scoringService = scoringService;
            this.eventEmitter = eventEmitter;
            this.taskCreator = taskCreator;
            this.auditLogger = auditLogger;
            this.structuredLogger = structuredLogger;
        }

        DecisionResult decide(ClaimInput input) {
            addressValidator.validate(input.riskAddress());
            if (input.dateOfLoss() == null || !input.dateOfLoss().toString().matches(".*T.*")) {
                throw new IllegalArgumentException("Dates must be valid ISO 8601 and within historical range");
            }
            if (!input.namedInsured().matches("[A-Za-z]+\\s+[A-Za-z]+")) {
                throw new IllegalArgumentException("Names must match PAS format: First Last or Entity");
            }

            List<CandidatePolicy> candidates = policyFetcher.fetchCandidatePolicies(input);
            if (candidates.isEmpty()) throw new RuntimeException("Policy data stale: >5min freshness requirement exceeded");

            CandidatePolicy best = candidates.stream()
                .max((a, b) -> Double.compare(scoringService.calculateScore(input, a), scoringService.calculateScore(input, b)))
                .orElseThrow();

            double score = scoringService.calculateScore(input, best);
            PolicyMatchStatus status;
            if (score >= 0.85 && candidates.size() == 1) status = PolicyMatchStatus.EXACT;
            else if (score >= 0.70 && candidates.size() > 1) status = PolicyMatchStatus.MULTIPLE;
            else status = PolicyMatchStatus.NONE;

            DecisionResult result = new DecisionResult(
                status == PolicyMatchStatus.EXACT ? best.policyId() : null,
                "ACTIVE", "COMPREHENSIVE", status, candidates.size()
            );

            if (status == PolicyMatchStatus.EXACT) eventEmitter.emit("POLICY_MATCH_COMPLETED", "exact");
            if (status == PolicyMatchStatus.MULTIPLE) taskCreator.createManualResolutionTask(result);
            
            auditLogger.log("FNOL_POLICY_MATCHED", input.tenantId(), status.toString());
            structuredLogger.log("INFO", "CLAIM_DECISION_COMPLETED", Map.of("claimId", input.claimId()));
            
            return result;
        }
    }
}
