package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntakeNormalizationDecisionTest {

    @Mock
    private NormalizationService normalizationService;

    @Mock
    private ValidationDecisionEngine validationDecisionEngine;

    @Mock
    private StructuredAuditLogger auditLogger;

    private MultiChannelFnolDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new MultiChannelFnolDecisionService(normalizationService, validationDecisionEngine, auditLogger);
    }

    @Test
    void applies_when_intake_submission_completes_normalization() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        String tenantId = "tenant-insurance-001";
        String idempotencyKey = UUID.randomUUID().toString();
        Instant auditTimestamp = Instant.now();
        Map<String, Object> normalizedPayload = Map.of(
                "channel", "web",
                "lossType", "collision",
                "severity", "low"
        );

        NormalizedIntakeSubmission submission = new NormalizedIntakeSubmission(
                claimId, tenantId, idempotencyKey, auditTimestamp, normalizedPayload
        );

        // Mock normalization completion side-effect
        doNothing().when(normalizationService).markAsNormalized(any());

        // Mock decision engine response
        DecisionResult mockDecision = new DecisionResult(claimId, "APPROVED", "Auto-approve low severity collision");
        when(validationDecisionEngine.evaluate(any())).thenReturn(mockDecision);

        // Act
        DecisionResult result = decisionService.processAndDecide(submission);

        // Assert
        assertNotNull(result);
        assertEquals("APPROVED", result.decisionStatus());
        verify(validationDecisionEngine, times(1)).evaluate(submission);
        verify(normalizationService, times(1)).markAsNormalized(submission.claimId());
        verify(auditLogger, times(1)).logEvent(eq("FNOL_DECISION_APPLIED"), any(Map.class));
    }

    // Supporting domain classes & interfaces for compilation
    record NormalizedIntakeSubmission(String claimId, String tenantId, String idempotencyKey, Instant auditTimestamp, Map<String, Object> payload) {}
    record DecisionResult(String claimId, String decisionStatus, String rationale) {}

    interface NormalizationService { void markAsNormalized(String claimId); }
    interface ValidationDecisionEngine { DecisionResult evaluate(NormalizedIntakeSubmission submission); }
    interface StructuredAuditLogger { void logEvent(String eventType, Map<String, Object> context); }

    static class MultiChannelFnolDecisionService {
        private final NormalizationService normalizationService;
        private final ValidationDecisionEngine validationDecisionEngine;
        private final StructuredAuditLogger auditLogger;

        MultiChannelFnolDecisionService(NormalizationService normalizationService, ValidationDecisionEngine validationDecisionEngine, StructuredAuditLogger auditLogger) {
            this.normalizationService = normalizationService;
            this.validationDecisionEngine = validationDecisionEngine;
            this.auditLogger = auditLogger;
        }

        DecisionResult processAndDecide(NormalizedIntakeSubmission submission) {
            // Input validation at service boundary
            if (submission == null || submission.claimId() == null || submission.tenantId() == null) {
                throw new IllegalArgumentException("Input validation at service boundary failed: claimId and tenantId are required");
            }
            // Trigger normalization completion side-effect
            normalizationService.markAsNormalized(submission.claimId());
            // Execute validation decision
            DecisionResult decision = validationDecisionEngine.evaluate(submission);
            // Structured logging for observability & SOC2 audit trail
            auditLogger.logEvent("FNOL_DECISION_APPLIED", Map.of(
                    "claimId", submission.claimId(),
                    "tenantId", submission.tenantId(),
                    "idempotencyKey", submission.idempotencyKey(),
                    "auditTimestamp", submission.auditTimestamp().toString(),
                    "decision", decision.decisionStatus()
            ));
            return decision;
        }
    }
}
