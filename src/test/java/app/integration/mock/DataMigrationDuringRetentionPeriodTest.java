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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataMigrationDuringRetentionPeriodTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private RetentionPolicyService retentionPolicyService;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private FnoValidationDecisionService fnolValidationDecisionService;

    private String tenantId;
    private String claimId;
    private String claimNumber;
    private String policyId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        tenantId = "tenant-001";
        claimId = UUID.randomUUID().toString();
        claimNumber = "FNOL-2024-001";
        policyId = UUID.randomUUID().toString();
        idempotencyKey = UUID.randomUUID().toString();
    }

    @Test
    void data_migration_during_retention_period() {
        // Arrange: Build domain entities with required fields and audit timestamps
        Claim claim = new Claim(claimId, claimNumber, tenantId, policyId, Instant.now(), Instant.now());
        RetentionContext retentionContext = new RetentionContext(tenantId, "GDPR_COMPLIANT_7YR", true);
        IdempotencyKey idempotencyKey = new IdempotencyKey(idempotencyKey, claimId);

        // Mock external persistence and policy validation (TLS in transit / least privilege IAM assumed)
        when(retentionPolicyService.validateRetentionPeriod(anyString(), anyString())).thenReturn(true);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        doNothing().when(auditLogger).log(anyString(), anyMap());

        // Act: Execute FNOL validation & decision pipeline
        DecisionResult result = fnolValidationDecisionService.processSubmission(
                claim, idempotencyKey, retentionContext
        );

        // Assert: Verify validation passed, retention policy applied, audit logged, and no duplicate writes
        assertNotNull(result, "Decision result must not be null");
        assertTrue(result.isValidationPassed(), "FNOL validation must pass during valid retention period");
        verify(claimRepository, times(1)).save(claim);
        verify(retentionPolicyService, times(1)).validateRetentionPeriod(tenantId, "GDPR_COMPLIANT_7YR");
        verify(auditLogger, times(1)).log(eq("DATA_MIGRATION_TRIGGERED"), argThat(ctx ->
                ctx.containsKey("tenant_id") &&
                ctx.containsKey("claim_id") &&
                ctx.containsKey("retention_policy") &&
                ctx.containsKey("idempotency_key")
        ));
    }

    // Static nested types to keep test self-contained and compilable
    static class Claim {
        private final String claimId;
        private final String claimNumber;
        private final String tenantId;
        private final String policyId;
        private final Instant createdAt;
        private final Instant updatedAt;

        public Claim(String claimId, String claimNumber, String tenantId, String policyId, Instant createdAt, Instant updatedAt) {
            this.claimId = claimId;
            this.claimNumber = claimNumber;
            this.tenantId = tenantId;
            this.policyId = policyId;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    static class DecisionResult {
        private final boolean validationPassed;
        public DecisionResult(boolean validationPassed) { this.validationPassed = validationPassed; }
        public boolean isValidationPassed() { return validationPassed; }
    }

    static class RetentionContext {
        private final String tenantId;
        private final String policyName;
        private final boolean isActive;
        public RetentionContext(String tenantId, String policyName, boolean isActive) {
            this.tenantId = tenantId;
            this.policyName = policyName;
            this.isActive = isActive;
        }
    }

    static class IdempotencyKey {
        private final String key;
        private final String entityId;
        public IdempotencyKey(String key, String entityId) {
            this.key = key;
            this.entityId = entityId;
        }
    }

    interface ClaimRepository {
        Claim save(Claim claim);
    }

    interface RetentionPolicyService {
        boolean validateRetentionPeriod(String tenantId, String policyName);
    }

    interface AuditLogger {
        void log(String event, Map<String, Object> context);
    }

    interface FnoValidationDecisionService {
        DecisionResult processSubmission(Claim claim, IdempotencyKey idempotencyKey, RetentionContext retentionContext);
    }
}
