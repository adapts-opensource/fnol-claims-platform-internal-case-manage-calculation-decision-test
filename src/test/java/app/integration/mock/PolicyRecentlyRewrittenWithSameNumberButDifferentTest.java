package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Multi-Channel FNOL Submission validation decision logic.
 * Verifies handling of policies recently rewritten with the same number but different effective date.
 */
@ExtendWith(MockitoExtension.class)
class MultiChannelFnolValidationDecisionPolicyRecentlyRewrittenWithSameNumberButDifferentTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private FnolValidationDecisionService fnolValidationDecisionService;

    @Test
    void policy_recently_rewritten_with_same_number_but_different_effective_date() {
        // Given: Policy exists but has been rewritten with a different effective date
        String tenantId = "tenant-newco-auto";
        String policyNumber = "POL-98765";
        String idempotencyKey = "idem-rewrite-diff-date-001";
        LocalDate newEffectiveDate = LocalDate.of(2023, 12, 1);
        LocalDate lossDate = LocalDate.of(2023, 11, 15);

        PolicyDto policyDto = new PolicyDto();
        policyDto.setPolicyNumber(policyNumber);
        policyDto.setEffectiveDate(newEffectiveDate);
        policyDto.setRewritten(true);
        policyDto.setTenantId(tenantId);

        when(policyValidationService.fetchPolicy(policyNumber, tenantId))
                .thenReturn(Optional.of(policyDto));

        // Idempotency check passes
        when(idempotencyService.validateIdempotency(idempotencyKey))
                .thenReturn(true);

        FnolSubmissionDto submissionDto = new FnolSubmissionDto();
        submissionDto.setPolicyNumber(policyNumber);
        submissionDto.setLossDate(lossDate);
        submissionDto.setTenantId(tenantId);
        submissionDto.setIdempotencyKey(idempotencyKey);

        // When: Submit FNOL for loss occurring before the new effective date
        ValidationDecision decision = fnolValidationDecisionService.evaluate(submissionDto);

        // Then: Decision should be blocked due to rewrite conflict
        assertNotNull(decision);
        assertEquals(DecisionOutcome.BLOCKED_POLICY_REWRITTEN, decision.getOutcome());
        assertTrue(decision.isAuditRequired());
        assertEquals(tenantId, decision.getTenantId());
        
        // Verify NFR: Idempotency key usage
        verify(idempotencyService).validateIdempotency(idempotencyKey);
        
        // Verify NFR: Policy validation called with tenant isolation
        verify(policyValidationService).fetchPolicy(policyNumber, tenantId);
        
        // Verify NFR: Audit logging triggered for blocked decision
        verify(auditLogService).logDecision(eq(decision), any());
    }

    // Minimal stubs for demonstration purposes
    static class PolicyDto {
        private String policyNumber;
        private String tenantId;
        private LocalDate effectiveDate;
        private boolean rewritten;

        public String getPolicyNumber() { return policyNumber; }
        public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public LocalDate getEffectiveDate() { return effectiveDate; }
        public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
        public boolean isRewritten() { return rewritten; }
        public void setRewritten(boolean rewritten) { this.rewritten = rewritten; }
    }

    static class FnolSubmissionDto {
        private String policyNumber;
        private String tenantId;
        private LocalDate lossDate;
        private String idempotencyKey;

        public String getPolicyNumber() { return policyNumber; }
        public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public LocalDate getLossDate() { return lossDate; }
        public void setLossDate(LocalDate lossDate) { this.lossDate = lossDate; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }

    static class ValidationDecision {
        private DecisionOutcome outcome;
        private String tenantId;
        private boolean auditRequired;

        public DecisionOutcome getOutcome() { return outcome; }
        public void setOutcome(DecisionOutcome outcome) { this.outcome = outcome; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public boolean isAuditRequired() { return auditRequired; }
        public void setAuditRequired(boolean auditRequired) { this.auditRequired = auditRequired; }
    }

    interface PolicyValidationService {
        Optional<PolicyDto> fetchPolicy(String policyNumber, String tenantId);
    }

    interface IdempotencyService {
        boolean validateIdempotency(String idempotencyKey);
    }

    interface AuditLogService {
        void logDecision(ValidationDecision decision, String context);
    }

    interface FnolValidationDecisionService {
        ValidationDecision evaluate(FnlSubmissionDto submissionDto);
    }
}
