package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionValidationDecisionTest {

    @Mock
    private AuditRequestService auditRequestService;

    @Mock
    private ValidationDecisionEngine validationDecisionEngine;

    @InjectMocks
    private FnolSubmissionProcessor fnolSubmissionProcessor;

    @BeforeEach
    void setUp() {
        // MockitoExtension initializes mocks and injects them automatically
    }

    @Test
    void applies_when_audit_request_initiated() {
        // Arrange
        String claimId = "CLM-2024-001";
        String tenantId = "tenant-1";
        String idempotencyKey = "idemp-key-001";

        when(auditRequestService.initiateClaimAudit(claimId, tenantId)).thenReturn(true);
        when(validationDecisionEngine.evaluateDecision(claimId, tenantId)).thenReturn("AUDIT_REQUIRED");

        // Act
        String decision = fnolSubmissionProcessor.processSubmission(claimId, tenantId, idempotencyKey);

        // Assert
        assertEquals("AUDIT_REQUIRED", decision, "Validation decision should apply when audit is initiated");
        verify(auditRequestService).initiateClaimAudit(claimId, tenantId);
        verify(validationDecisionEngine).evaluateDecision(claimId, tenantId);
        verifyNoMoreInteractions(auditRequestService, validationDecisionEngine);
    }

    // Minimal domain interfaces for mock isolation and compilation
    interface AuditRequestService {
        boolean initiateClaimAudit(String claimId, String tenantId);
    }

    interface ValidationDecisionEngine {
        String evaluateDecision(String claimId, String tenantId);
    }

    static class FnolSubmissionProcessor {
        private final AuditRequestService auditRequestService;
        private final ValidationDecisionEngine validationDecisionEngine;

        FnolSubmissionProcessor(AuditRequestService auditRequestService, ValidationDecisionEngine validationDecisionEngine) {
            this.auditRequestService = auditRequestService;
            this.validationDecisionEngine = validationDecisionEngine;
        }

        String processSubmission(String claimId, String tenantId, String idempotencyKey) {
            // Simulate audit request initiation per feature requirement
            auditRequestService.initiateClaimAudit(claimId, tenantId);
            // Simulate validation decision evaluation
            return validationDecisionEngine.evaluateDecision(claimId, tenantId);
        }
    }
}
