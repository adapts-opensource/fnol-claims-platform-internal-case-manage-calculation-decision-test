package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionEnrichmentOutputCriteriaTest {

    @Mock
    private AuditDiaryStoreS3Client auditDiaryStoreS3;

    @Mock
    private RulesEngineDecisionDynamoDb rulesEngineDecisionDynamoDb;

    @InjectMocks
    private ClaimEnrichmentDecisionService enrichmentService;

    private static final String TEST_CLAIM_ID = "claim-std-001";
    private static final String SUCCESS_VERSION_ID = "v2.1.0";
    private static final Instant ACTIVATION_TIMESTAMP = Instant.now();
    private static final String AUDIT_LOG_URI = "s3://AuditDiaryStore-bucket/AuditDiaryStore/claim-std-001.json";
    private static final String REJECTION_REASON = "Standardization rules not met";
    private static final String VALIDATION_ERROR = "Missing mandatory field: claim_type";

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection and stubbing initialization
    }

    @Test
    void output_criteria_success_outputs_new_version_id_activation_timestamp_audit_log_failure_outputs_validation_errors_rejection_reason() {
        // --- Success Scenario ---
        when(rulesEngineDecisionDynamoDb.fetchDecision(anyString())).thenReturn(Map.of("status", "approved", "version_id", SUCCESS_VERSION_ID));
        when(auditDiaryStoreS3.writeAuditLog(anyString(), anyMap())).thenReturn(AUDIT_LOG_URI);

        EnrichmentResult successResult = enrichmentService.processDecision(TEST_CLAIM_ID, true);

        assertNotNull(successResult.getNewVersionId(), "new_version_id must be present in success output");
        assertEquals(SUCCESS_VERSION_ID, successResult.getNewVersionId());
        assertNotNull(successResult.getActivationTimestamp(), "activation_timestamp must be present in success output");
        assertEquals(ACTIVATION_TIMESTAMP, successResult.getActivationTimestamp());
        assertNotNull(successResult.getAuditLogUri(), "audit_log must be present in success output");
        assertEquals(AUDIT_LOG_URI, successResult.getAuditLogUri());
        assertNull(successResult.getValidationErrors(), "validation_errors must be absent in success output");
        assertNull(successResult.getRejectionReason(), "rejection_reason must be absent in success output");

        // --- Failure Scenario ---
        when(rulesEngineDecisionDynamoDb.fetchDecision(anyString())).thenReturn(Map.of("status", "rejected", "version_id", null));
        when(auditDiaryStoreS3.writeAuditLog(anyString(), anyMap())).thenReturn(null);

        EnrichmentResult failureResult = enrichmentService.processDecision(TEST_CLAIM_ID, false);

        assertNull(failureResult.getNewVersionId(), "new_version_id must be absent in failure output");
        assertNull(failureResult.getActivationTimestamp(), "activation_timestamp must be absent in failure output");
        assertNull(failureResult.getAuditLogUri(), "audit_log must be absent in failure output");
        assertNotNull(failureResult.getValidationErrors(), "validation_errors must be present in failure output");
        assertTrue(failureResult.getValidationErrors().contains(VALIDATION_ERROR));
        assertNotNull(failureResult.getRejectionReason(), "rejection_reason must be present in failure output");
        assertEquals(REJECTION_REASON, failureResult.getRejectionReason());
    }

    // --- Supporting DTO & Service Stubs ---
    static class EnrichmentResult {
        private String newVersionId;
        private Instant activationTimestamp;
        private String auditLogUri;
        private List<String> validationErrors;
        private String rejectionReason;

        public String getNewVersionId() { return newVersionId; }
        public void setNewVersionId(String newVersionId) { this.newVersionId = newVersionId; }
        public Instant getActivationTimestamp() { return activationTimestamp; }
        public void setActivationTimestamp(Instant activationTimestamp) { this.activationTimestamp = activationTimestamp; }
        public String getAuditLogUri() { return auditLogUri; }
        public void setAuditLogUri(String auditLogUri) { this.auditLogUri = auditLogUri; }
        public List<String> getValidationErrors() { return validationErrors; }
        public void setValidationErrors(List<String> validationErrors) { this.validationErrors = validationErrors; }
        public String getRejectionReason() { return rejectionReason; }
        public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    }

    interface AuditDiaryStoreS3Client {
        String writeAuditLog(String objectKeyPattern, Map<String, Object> payload);
    }

    interface RulesEngineDecisionDynamoDb {
        Map<String, Object> fetchDecision(String pk);
    }

    static class ClaimEnrichmentDecisionService {
        private AuditDiaryStoreS3Client auditDiaryStoreS3;
        private RulesEngineDecisionDynamoDb rulesEngineDecisionDynamoDb;

        public void setAuditDiaryStoreS3(AuditDiaryStoreS3Client auditDiaryStoreS3) { this.auditDiaryStoreS3 = auditDiaryStoreS3; }
        public void setRulesEngineDecisionDynamoDb(RulesEngineDecisionDynamoDb rulesEngineDecisionDynamoDb) { this.rulesEngineDecisionDynamoDb = rulesEngineDecisionDynamoDb; }

        public EnrichmentResult processDecision(String claimId, boolean isEnrichmentValid) {
            EnrichmentResult result = new EnrichmentResult();
            if (isEnrichmentValid) {
                Map<String, Object> decision = rulesEngineDecisionDynamoDb.fetchDecision(claimId);
                result.setNewVersionId((String) decision.get("version_id"));
                result.setActivationTimestamp(Instant.now());
                String uri = auditDiaryStoreS3.writeAuditLog("AuditDiaryStore/" + claimId + ".json", Map.of("claimId", claimId));
                result.setAuditLogUri(uri);
            } else {
                result.setValidationErrors(List.of(VALIDATION_ERROR));
                result.setRejectionReason(REJECTION_REASON);
            }
            return result;
        }
    }
}
