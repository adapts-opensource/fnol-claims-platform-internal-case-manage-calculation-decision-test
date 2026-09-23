package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ThresholdMisconfigurationOverrideWithAuditTest {

    @Mock
    private DecisionTransformationService decisionTransformationService;

    @Mock
    private AuditService auditService;

    private ThresholdMisconfigurationOverrideWithAuditSut sut;

    @BeforeEach
    void setUp() {
        sut = new ThresholdMisconfigurationOverrideWithAuditSut(decisionTransformationService, auditService);
    }

    @Test
    void threshold_misconfiguration_override_with_audit() {
        // Arrange
        String reserveId = "res-8a9b1c";
        double thresholdLimit = 5000.0;
        double claimAmount = 7500.0;
        String overrideReason = "Expedited processing approved";
        String expectedAuditTraceId = "audit-trace-42";

        when(decisionTransformationService.isThresholdMisconfigured(eq(reserveId), eq(claimAmount), eq(thresholdLimit)))
                .thenReturn(true);
        when(decisionTransformationService.applyOverride(eq(reserveId), eq(claimAmount), eq(overrideReason)))
                .thenReturn(true);
        when(auditService.logStructuredAudit(anyString(), anyString(), anyString(), any()))
                .thenReturn(expectedAuditTraceId);

        // Act
        String actualTraceId = sut.executeOverrideWithAudit(reserveId, claimAmount, thresholdLimit, overrideReason);

        // Assert
        assertNotNull(actualTraceId);
        assertEquals(expectedAuditTraceId, actualTraceId);

        verify(auditService).logStructuredAudit(
                eq("THRESHOLD_MISCONFIGURATION"),
                eq(reserveId),
                eq("DETECTED"),
                any()
        );
        verify(auditService).logStructuredAudit(
                eq("OVERRIDE_APPLIED"),
                eq(reserveId),
                eq("SUCCESS"),
                any()
        );
        verify(decisionTransformationService).applyOverride(eq(reserveId), eq(claimAmount), eq(overrideReason));
    }

    // SUT encapsulating the transformation & audit orchestration flow
    private static class ThresholdMisconfigurationOverrideWithAuditSut {
        private final DecisionTransformationService decisionTransformationService;
        private final AuditService auditService;

        ThresholdMisconfigurationOverrideWithAuditSut(DecisionTransformationService decisionTransformationService, AuditService auditService) {
            this.decisionTransformationService = decisionTransformationService;
            this.auditService = auditService;
        }

        String executeOverrideWithAudit(String reserveId, double claimAmount, double thresholdLimit, String overrideReason) {
            boolean misconfigured = decisionTransformationService.isThresholdMisconfigured(reserveId, claimAmount, thresholdLimit);
            if (!misconfigured) {
                throw new IllegalArgumentException("Threshold misconfiguration not detected");
            }
            boolean overrideApplied = decisionTransformationService.applyOverride(reserveId, claimAmount, overrideReason);
            if (!overrideApplied) {
                throw new IllegalStateException("Override application failed");
            }
            return auditService.logStructuredAudit("OVERRIDE_APPLIED", reserveId, "SUCCESS", new Object());
        }
    }

    // Mocked domain interfaces representing external/config services
    private interface DecisionTransformationService {
        boolean isThresholdMisconfigured(String reserveId, double claimAmount, double thresholdLimit);
        boolean applyOverride(String reserveId, double claimAmount, String overrideReason);
    }

    private interface AuditService {
        String logStructuredAudit(String eventType, String reserveId, String status, Object metadata);
    }
}
