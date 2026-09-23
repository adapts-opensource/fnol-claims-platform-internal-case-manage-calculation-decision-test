package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class DecisionRetentionComplianceRuleIfRequestOutsideRetentionTest {

    @Mock
    private RetentionPolicyService retentionPolicyService;

    @Mock
    private ComplianceAuditLogger auditLogger;

    private InsuredEngagementDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new InsuredEngagementDecisionService(retentionPolicyService, auditLogger);
    }

    @Test
    void decision_retention_compliance_rule_if_request_outside_retention_deny_with_guidance_expected_outcome_return_error_log_attempt() {
        // Arrange
        String requestId = "REQ-RET-OUTSIDE-001";
        when(retentionPolicyService.isWithinRetention(requestId)).thenReturn(false);

        // Act & Assert: Expect error return
        Exception thrown = assertThrows(RuntimeException.class, () -> decisionService.processDecision(requestId));
        assertTrue(thrown.getMessage().contains("outside retention"), "Should return error indicating retention violation");
        assertTrue(thrown.getMessage().contains("guidance"), "Should include compliance guidance");

        // Verify logging attempt
        verify(auditLogger, times(1)).logAttempt(eq(requestId), eq("DENIED"), anyString());
    }

    // Minimal domain interfaces/classes for compilation and mock isolation
    interface RetentionPolicyService {
        boolean isWithinRetention(String requestId);
    }

    interface ComplianceAuditLogger {
        void logAttempt(String requestId, String status, String message);
    }

    static class InsuredEngagementDecisionService {
        private final RetentionPolicyService retentionPolicyService;
        private final ComplianceAuditLogger auditLogger;

        InsuredEngagementDecisionService(RetentionPolicyService retentionPolicyService, ComplianceAuditLogger auditLogger) {
            this.retentionPolicyService = retentionPolicyService;
            this.auditLogger = auditLogger;
        }

        void processDecision(String requestId) {
            if (!retentionPolicyService.isWithinRetention(requestId)) {
                String guidance = "Request denied: outside retention period. Contact support for guidance.";
                auditLogger.logAttempt(requestId, "DENIED", guidance);
                throw new RuntimeException("Error: " + guidance);
            }
        }
    }
}
