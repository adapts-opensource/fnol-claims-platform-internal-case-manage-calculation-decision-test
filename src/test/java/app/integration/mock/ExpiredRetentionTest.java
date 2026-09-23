package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;

public class MultiChannelFnolValidationDecisionTest {

    @Mock
    private PolicyRetentionCheckService policyRetentionCheckService;

    @Mock
    private StructuredLogger logger;

    private MultiChannelFnolValidationDecisionValidator validator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new MultiChannelFnolValidationDecisionValidator(policyRetentionCheckService, logger);
    }

    @Test
    void expired_retention() {
        // Arrange
        String idempotencyKey = "idemp-key-expired-ret-001";
        String tenantId = "newco-insurance-tenant";
        String policyId = "pol-exp-123";
        String claimId = "clm-456";
        String claimNumber = "FNOL-2024-EXP-001";
        Instant submittedAt = Instant.now();

        // Mock expired retention state (external I/O is mocked, never calls live AWS/HTTP)
        when(policyRetentionCheckService.isRetentionActive(policyId, tenantId)).thenReturn(false);

        // Build validation context with required tenant_id and audit timestamp
        FnolValidationContext context = new FnolValidationContext(
                idempotencyKey, tenantId, policyId, claimId, claimNumber, submittedAt
        );

        // Act
        ValidationDecision decision = validator.evaluate(context);

        // Assert decision outcome
        assertNotNull(decision);
        assertEquals(ValidationOutcome.REJECTED, decision.outcome());
        assertEquals("ExpiredRetention", decision.code());
        assertTrue(decision.message().contains("retention period"));
        assertEquals(ValidationSource.POLICY, decision.source());

        // Verify external I/O contract was invoked correctly
        verify(policyRetentionCheckService).isRetentionActive(policyId, tenantId);
        verifyNoMoreInteractions(policyRetentionCheckService);

        // Verify structured logging for observability & GDPR/SOC2 audit trail
        ArgumentCaptor<Map<String, String>> logCaptor = ArgumentCaptor.forClass(Map.class);
        verify(logger).info(eq("FNOL validation decision"), logCaptor.capture());
        Map<String, String> logMap = logCaptor.getValue();
        assertEquals(idempotencyKey, logMap.get("idempotencyKey"));
        assertEquals(tenantId, logMap.get("tenantId"));
        assertEquals("REJECTED", logMap.get("decisionOutcome"));
    }

    // Minimal validator implementation for test isolation
    private static class MultiChannelFnolValidationDecisionValidator {
        private final PolicyRetentionCheckService retentionService;
        private final StructuredLogger logger;

        MultiChannelFnolValidationDecisionValidator(PolicyRetentionCheckService retentionService, StructuredLogger logger) {
            this.retentionService = retentionService;
            this.logger = logger;
        }

        ValidationDecision evaluate(FnolValidationContext context) {
            boolean isActive = retentionService.isRetentionActive(context.policyId(), context.tenantId());
            if (!isActive) {
                var decision = new ValidationDecision(
                        ValidationOutcome.REJECTED,
                        "ExpiredRetention",
                        "Policy retention period has expired.",
                        ValidationSource.POLICY
                );
                logger.info("FNOL validation decision", Map.of(
                        "idempotencyKey", context.idempotencyKey(),
                        "tenantId", context.tenantId(),
                        "policyId", context.policyId(),
                        "decisionOutcome", decision.outcome().name()
                ));
                return decision;
            }
            return new ValidationDecision(
                    ValidationOutcome.ACCEPTED,
                    "Valid",
                    "Retention period active.",
                    ValidationSource.POLICY
            );
        }
    }

    private interface PolicyRetentionCheckService {
        boolean isRetentionActive(String policyId, String tenantId);
    }

    private interface StructuredLogger {
        void info(String message, Map<String, String> context);
    }

    private record FnolValidationContext(
            String idempotencyKey,
            String tenantId,
            String policyId,
            String claimId,
            String claimNumber,
            Instant submittedAt
    ) {}

    private record ValidationDecision(
            ValidationOutcome outcome,
            String code,
            String message,
            ValidationSource source
    ) {}

    private enum ValidationOutcome { ACCEPTED, REJECTED, PENDING }
    private enum ValidationSource { POLICY, SYSTEM, EXTERNAL }
}
