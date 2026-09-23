package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RapidSuccessiveSubmissionsFromSameUserTest {

    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private EmailNotificationService emailNotificationService;

    @InjectMocks
    private FnolSubmissionService fnolSubmissionService;

    private static final String TENANT_ID = "carrier_tenant_01";
    private static final String POLICY_ID = "pol_98765";
    private static final String USER_EMAIL = "policyholder@newco.com";

    @BeforeEach
    void setUp() {
        lenient().when(idempotencyService.acquireLock(anyString())).thenReturn(true);
        lenient().when(claimRepository.save(any())).thenAnswer(invocation -> {
            var claim = new Claim();
            claim.setClaimId(UUID.randomUUID().toString());
            claim.setClaimNumber("FNOL-" + System.currentTimeMillis());
            claim.setTenantId(TENANT_ID);
            claim.setPolicyId(POLICY_ID);
            claim.setCreatedAt(Instant.now());
            claim.setUpdatedAt(Instant.now());
            return claim;
        });
    }

    @Test
    void rapid_successive_submissions_from_same_user() {
        // Arrange: Simulate rapid successive calls with unique idempotency keys
        String idempotencyKeyA = UUID.randomUUID().toString();
        String idempotencyKeyB = UUID.randomUUID().toString();
        String duplicateIdempotencyKey = idempotencyKeyA;

        var submissionRequestA = new FnolSubmissionRequest(idempotencyKeyA, TENANT_ID, POLICY_ID, USER_EMAIL);
        var submissionRequestB = new FnolSubmissionRequest(idempotencyKeyB, TENANT_ID, POLICY_ID, USER_EMAIL);
        var duplicateRequest = new FnolSubmissionRequest(duplicateIdempotencyKey, TENANT_ID, POLICY_ID, USER_EMAIL);

        // Mock idempotency behavior: first two succeed, duplicate fails
        when(idempotencyService.acquireLock(eq(idempotencyKeyA))).thenReturn(true);
        when(idempotencyService.acquireLock(eq(idempotencyKeyB))).thenReturn(true);
        when(idempotencyService.acquireLock(eq(duplicateIdempotencyKey))).thenReturn(false);

        // Act & Assert: First rapid submission
        assertDoesNotThrow(() -> fnolSubmissionService.processSubmission(submissionRequestA));
        verify(claimRepository, times(1)).save(any());
        verify(emailNotificationService, times(1)).sendAcknowledgement(eq(USER_EMAIL), anyString());

        // Act & Assert: Second rapid submission (different key)
        assertDoesNotThrow(() -> fnolSubmissionService.processSubmission(submissionRequestB));
        verify(claimRepository, times(2)).save(any());
        verify(emailNotificationService, times(2)).sendAcknowledgement(eq(USER_EMAIL), anyString());

        // Act & Assert: Rapid duplicate submission (same key)
        assertThrows(DuplicateSubmissionException.class, () -> fnolSubmissionService.processSubmission(duplicateRequest));
        verify(claimRepository, times(2)).save(any()); // No additional persistence
        verifyNoMoreInteractions(emailNotificationService); // No duplicate emails
    }

    // Minimal static DTOs & Interfaces for compilation & test isolation
    static class FnolSubmissionRequest {
        final String idempotencyKey;
        final String tenantId;
        final String policyId;
        final String userEmail;

        FnolSubmissionRequest(String idempotencyKey, String tenantId, String policyId, String userEmail) {
            this.idempotencyKey = idempotencyKey;
            this.tenantId = tenantId;
            this.policyId = policyId;
            this.userEmail = userEmail;
        }
    }

    static class Claim {
        String claimId, claimNumber, tenantId, policyId;
        Instant createdAt, updatedAt;
        public void setClaimId(String id) { claimId = id; }
        public void setClaimNumber(String num) { claimNumber = num; }
        public void setTenantId(String id) { tenantId = id; }
        public void setPolicyId(String id) { policyId = id; }
        public void setCreatedAt(Instant t) { createdAt = t; }
        public void setUpdatedAt(Instant t) { updatedAt = t; }
    }

    interface IdempotencyService {
        boolean acquireLock(String key);
    }

    interface ClaimRepository {
        Claim save(Claim claim);
    }

    interface EmailNotificationService {
        void sendAcknowledgement(String email, String claimId);
    }

    static class DuplicateSubmissionException extends RuntimeException {
        DuplicateSubmissionException(String msg) { super(msg); }
    }

    class FnolSubmissionService {
        private final IdempotencyService idempotencyService;
        private final ClaimRepository claimRepository;
        private final EmailNotificationService emailNotificationService;

        FnolSubmissionService(IdempotencyService idempotencyService, ClaimRepository claimRepository, EmailNotificationService emailNotificationService) {
            this.idempotencyService = idempotencyService;
            this.claimRepository = claimRepository;
            this.emailNotificationService = emailNotificationService;
        }

        void processSubmission(FnlSubmissionRequest request) {
            if (!idempotencyService.acquireLock(request.idempotencyKey)) {
                throw new DuplicateSubmissionException("Idempotency key already consumed: " + request.idempotencyKey);
            }
            Claim claim = new Claim();
            claim.setTenantId(request.tenantId);
            claim.setPolicyId(request.policyId);
            Claim saved = claimRepository.save(claim);
            emailNotificationService.sendAcknowledgement(request.userEmail, saved.claimId);
        }
    }
}
