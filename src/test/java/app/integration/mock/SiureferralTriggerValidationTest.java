package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SiuReferralTriggerValidationTest {

    @Mock
    private FraudScoringService fraudScoringService;
    @Mock
    private TaskCreationService taskCreationService;
    @Mock
    private StructuredLoggingService loggingService;
    @Mock
    private GdprMinimizationService gdprMinimizationService;
    @Mock
    private DocumentStoreService documentStoreService;
    @Mock
    private ClaimDataStoreService claimDataStoreService;

    private ClaimDataStandardizationDecisionValidator validator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new ClaimDataStandardizationDecisionValidator(
                fraudScoringService,
                taskCreationService,
                loggingService,
                gdprMinimizationService,
                documentStoreService,
                claimDataStoreService
        );
    }

    @Test
    @DisplayName("validate_siu_referral_candidate_indicators")
    void validate_siu_referral_candidate_indicators() {
        // Arrange: Map inputs to payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "claim-789");
        payload.put("policy_inception_days_ago", 5);
        payload.put("prior_similar_claims", 2);
        payload.put("conflicting_facts", true);
        payload.put("late_reporting_days", 45);
        payload.put("fraud_watchlist_match", false);
        payload.put("reporter_data", Map.of("name", "John Doe", "contact", "john@example.com"));

        double configuredThreshold = 75.0;
        double expectedFraudScore = 82.5;
        when(fraudScoringService.calculateScore(payload)).thenReturn(expectedFraudScore);

        // Act: Execute validation logic
        ValidationResult result = validator.evaluate(payload, configuredThreshold);

        // Assert: Fraud score exceeds threshold
        assertTrue(result.fraudScoreExceedsThreshold(), "Fraud score must exceed configured threshold");
        assertEquals(expectedFraudScore, result.fraudScore(), "Fraud score must match calculated value");

        // Assert: Initial claim type set to SIU referral candidate
        assertEquals("SIU_REFERRAL_CANDIDATE", result.initialClaimType(), "Initial claim type must be SIU_REFERRAL_CANDIDATE");

        // Assert: SIU Referral Review task created
        verify(taskCreationService, times(1)).createTask(eq("SIU_REFERRAL_REVIEW"), eq("claim-789"), any());

        // Assert: Structured logging captures rule execution and payload validation
        ArgumentCaptor<Map<String, Object>> logContextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(loggingService, times(1)).logStructured(eq("RULE_EXECUTION"), logContextCaptor.capture());
        Map<String, Object> loggedContext = logContextCaptor.getValue();
        assertTrue(loggedContext.containsKey("payload_validation"), "Structured log must capture payload validation context");
        assertTrue(loggedContext.containsKey("rule_execution_id"), "Structured log must capture rule execution metadata");

        // Assert: GDPR minimization flags applied to reporter data
        verify(gdprMinimizationService, times(1)).applyMinimizationFlags(payload, "reporter_data");
        assertTrue(payload.containsKey("_gdpr_minimized"), "Reporter data must have GDPR minimization flag applied");
        assertEquals(true, payload.get("_gdpr_minimized"), "GDPR minimization flag must be explicitly set");
    }

    // --- Supporting Interfaces & Classes for Compilation Context ---

    interface FraudScoringService {
        double calculateScore(Map<String, Object> payload);
    }

    interface TaskCreationService {
        void createTask(String taskType, String claimId, Map<String, Object> metadata);
    }

    interface StructuredLoggingService {
        void logStructured(String event, Map<String, Object> context);
    }

    interface GdprMinimizationService {
        void applyMinimizationFlags(Map<String, Object> payload, String fieldGroup);
    }

    interface DocumentStoreService {
        String uploadDocument(String bucketName, String objectKeyPattern, byte[] data);
    }

    interface ClaimDataStoreService {
        void saveClaimItem(String tableName, Map<String, Object> itemPayload);
    }

    record ValidationResult(double fraudScore, boolean fraudScoreExceedsThreshold, String initialClaimType) {}

    class ClaimDataStandardizationDecisionValidator {
        private final FraudScoringService fraudScoringService;
        private final TaskCreationService taskCreationService;
        private final StructuredLoggingService loggingService;
        private final GdprMinimizationService gdprMinimizationService;
        private final DocumentStoreService documentStoreService;
        private final ClaimDataStoreService claimDataStoreService;

        ClaimDataStandardizationDecisionValidator(FraudScoringService fraudScoringService,
                                                  TaskCreationService taskCreationService,
                                                  StructuredLoggingService loggingService,
                                                  GdprMinimizationService gdprMinimizationService,
                                                  DocumentStoreService documentStoreService,
                                                  ClaimDataStoreService claimDataStoreService) {
            this.fraudScoringService = fraudScoringService;
            this.taskCreationService = taskCreationService;
            this.loggingService = loggingService;
            this.gdprMinimizationService = gdprMinimizationService;
            this.documentStoreService = documentStoreService;
            this.claimDataStoreService = claimDataStoreService;
        }

        ValidationResult evaluate(Map<String, Object> payload, double threshold) {
            double score = fraudScoringService.calculateScore(payload);
            boolean exceedsThreshold = score > threshold;

            gdprMinimizationService.applyMinimizationFlags(payload, "reporter_data");
            payload.put("_gdpr_minimized", true);

            if (exceedsThreshold) {
                taskCreationService.createTask("SIU_REFERRAL_REVIEW", (String) payload.get("id"), Map.of("reason", "high_fraud_score"));
                Map<String, Object> logContext = Map.of(
                        "payload_validation", "passed",
                        "rule_execution_id", "exec-siu-" + payload.get("id")
                );
                loggingService.logStructured("RULE_EXECUTION", logContext);
                return new ValidationResult(score, true, "SIU_REFERRAL_CANDIDATE");
            }
            return new ValidationResult(score, false, "STANDARD");
        }
    }
}
