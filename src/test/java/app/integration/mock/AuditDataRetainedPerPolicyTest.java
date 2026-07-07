package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditDataRetainedPerPolicyTest {

    // Mocked infrastructure contracts per spec
    interface DocumentStoreService {
        String storeAuditData(String policyId, String entityKey, Map<String, Object> payload);
    }

    interface PolicyValidationService {
        boolean validatePayload(Map<String, Object> payload);
    }

    // Service under test
    @InjectMocks
    private ClaimDataStandardizationService claimDataStandardizationService;

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Test
    void audit_data_retained_per_policy() {
        // Arrange
        String policyId = "POL-123456";
        String claimId = "CLM-789012";
        Map<String, Object> claimPayload = Map.of("id", claimId, "payload", Map.of("type", "FNOL", "status", "SUBMITTED"));
        Map<String, Object> auditPayload = Map.of("policyId", policyId, "claimId", claimId, "action", "DECISION", "retained", true);

        when(policyValidationService.validatePayload(claimPayload)).thenReturn(true);
        when(documentStoreService.storeAuditData(eq(policyId), anyString(), eq(auditPayload)))
                .thenReturn("s3://DocumentStoreService-bucket/audit/" + policyId + ".json");

        // Act
        String result = claimDataStandardizationService.processClaim(claimId, claimPayload);

        // Assert
        assertEquals("COMPLETED", result);
        verify(policyValidationService, times(1)).validatePayload(claimPayload);
        verify(documentStoreService, times(1)).storeAuditData(eq(policyId), anyString(), eq(auditPayload));
    }

    // Implementation matching the mock structure
    static class ClaimDataStandardizationService {
        private final DocumentStoreService documentStoreService;
        private final PolicyValidationService policyValidationService;

        ClaimDataStandardizationService(DocumentStoreService documentStoreService, PolicyValidationService policyValidationService) {
            this.documentStoreService = documentStoreService;
            this.policyValidationService = policyValidationService;
        }

        public String processClaim(String claimId, Map<String, Object> payload) {
            if (!policyValidationService.validatePayload(payload)) {
                throw new IllegalArgumentException("Validation failed for claim: " + claimId);
            }
            String policyId = (String) payload.get("policyId");
            if (policyId == null) {
                policyId = "DEFAULT-POLICY";
            }
            Map<String, Object> auditData = Map.of("policyId", policyId, "claimId", claimId, "action", "DECISION", "retained", true);
            documentStoreService.storeAuditData(policyId, "claim_data_standardization_transformation_valida", auditData);
            return "COMPLETED";
        }
    }
}
