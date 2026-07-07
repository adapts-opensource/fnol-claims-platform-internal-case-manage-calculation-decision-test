package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

/**
 * Verifies that during claim initiation orchestration, an active policy is correctly 
 * preferred over an expired policy when both are associated with the same claim context.
 * Thread-safe, stateless, and fully mocks external I/O (DynamoDB, S3).
 */
@ExtendWith(MockitoExtension.class)
class ActivePolicyPreferredOverExpiredTest {

    @Mock
    private PolicyClaimsDbClient policyDbClient;

    @Mock
    private ComplianceAuditS3Client auditS3Client;

    private ClaimTransformationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimTransformationOrchestrator(policyDbClient, auditS3Client);
    }

    @Test
    void active_policy_preferred_over_expired() {
        // Arrange
        String claimId = "CLM-789";
        String activePolicyId = "POL-ACT-001";
        String expiredPolicyId = "POL-EXP-002";

        Map<String, Object> activePolicyRecord = Map.of(
                "policyId", activePolicyId,
                "status", "ACTIVE",
                "coverageType", "AUTO"
        );
        Map<String, Object> expiredPolicyRecord = Map.of(
                "policyId", expiredPolicyId,
                "status", "EXPIRED",
                "coverageType", "AUTO"
        );

        when(policyDbClient.queryByClaimContext(claimId))
                .thenReturn(List.of(activePolicyRecord, expiredPolicyRecord));

        ClaimInitiationRequest request = new ClaimInitiationRequest(claimId, List.of(activePolicyId, expiredPolicyId));

        // Act
        TransformedClaimPayload result = orchestrator.transform(request);

        // Assert
        assertNotNull(result, "Transformed payload should not be null");
        assertEquals(activePolicyId, result.getPreferredPolicyId(), "Active policy should be preferred");
        assertEquals("ACTIVE", result.getPreferredPolicyStatus(), "Status should be ACTIVE");
        assertEquals(1, result.getPolicyCount(), "Should contain one preferred policy");

        verify(policyDbClient, times(1)).queryByClaimContext(claimId);
        verify(auditS3Client, times(1)).writeAuditLog(anyString(), anyString());
    }

    // Domain & Infrastructure Abstractions (Mocked)
    static class ClaimInitiationRequest {
        private final String claimId;
        private final List<String> policyIds;

        ClaimInitiationRequest(String claimId, List<String> policyIds) {
            this.claimId = claimId;
            this.policyIds = policyIds;
        }

        public String getClaimId() { return claimId; }
        public List<String> getPolicyIds() { return policyIds; }
    }

    static class TransformedClaimPayload {
        private String preferredPolicyId;
        private String preferredPolicyStatus;
        private int policyCount;

        public String getPreferredPolicyId() { return preferredPolicyId; }
        public void setPreferredPolicyId(String preferredPolicyId) { this.preferredPolicyId = preferredPolicyId; }
        public String getPreferredPolicyStatus() { return preferredPolicyStatus; }
        public void setPreferredPolicyStatus(String preferredPolicyStatus) { this.preferredPolicyStatus = preferredPolicyStatus; }
        public int getPolicyCount() { return policyCount; }
        public void setPolicyCount(int policyCount) { this.policyCount = policyCount; }
    }

    interface PolicyClaimsDbClient {
        List<Map<String, Object>> queryByClaimContext(String claimId);
    }

    interface ComplianceAuditS3Client {
        void writeAuditLog(String bucketName, String objectKeyPattern);
    }

    static class ClaimTransformationOrchestrator {
        private final PolicyClaimsDbClient policyDbClient;
        private final ComplianceAuditS3Client auditS3Client;

        ClaimTransformationOrchestrator(PolicyClaimsDbClient policyDbClient, ComplianceAuditS3Client auditS3Client) {
            this.policyDbClient = policyDbClient;
            this.auditS3Client = auditS3Client;
        }

        TransformedClaimPayload transform(ClaimInitiationRequest request) {
            List<Map<String, Object>> records = policyDbClient.queryByClaimContext(request.getClaimId());
            
            // Orchestration: Prefer ACTIVE over EXPIRED
            Map<String, Object> preferredRecord = records.stream()
                    .filter(r -> "ACTIVE".equals(r.get("status")))
                    .findFirst()
                    .orElse(records.get(0));

            TransformedClaimPayload payload = new TransformedClaimPayload();
            payload.setPreferredPolicyId((String) preferredRecord.get("policyId"));
            payload.setPreferredPolicyStatus((String) preferredRecord.get("status"));
            payload.setPolicyCount(1);

            // Observability & Compliance: Audit write (mocked)
            auditS3Client.writeAuditLog("ComplianceAuditService-bucket", "ComplianceAuditService/" + request.getClaimId() + ".json");
            return payload;
        }
    }
}
