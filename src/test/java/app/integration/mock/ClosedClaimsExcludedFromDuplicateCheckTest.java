package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock test for Claim Initiation & Routing: orchestration: transformation.
 * Verifies that closed claims are excluded from duplicate checks during transformation.
 */
class ClosedClaimsExcludedFromDuplicateCheckTest {

    // Mock interfaces representing Infrastructure IO Contracts
    interface PolicyClaimsDBService {
        String getClaimStatus(String claimId);
    }

    interface DuplicateCheckService {
        boolean checkDuplicate(String claimId);
    }

    interface ComplianceAuditService {
        void logAuditEvent(String claimId, String actionCode);
    }

    interface DocumentStorageService {
        String storeDocument(String bucketName, String objectKeyPattern, String payload);
    }

    // System Under Test
    static class ClaimTransformationOrchestrator {
        private PolicyClaimsDBService policyClaimsDB;
        private DuplicateCheckService duplicateCheckService;
        private ComplianceAuditService complianceAuditService;
        private DocumentStorageService documentStorageService;

        public ClaimTransformationOrchestrator(
                PolicyClaimsDBService policyClaimsDB,
                DuplicateCheckService duplicateCheckService,
                ComplianceAuditService complianceAuditService,
                DocumentStorageService documentStorageService) {
            this.policyClaimsDB = policyClaimsDB;
            this.duplicateCheckService = duplicateCheckService;
            this.complianceAuditService = complianceAuditService;
            this.documentStorageService = documentStorageService;
        }

        public String initiateAndTransform(String claimId, String payload) {
            String status = policyClaimsDB.getClaimStatus(claimId);
            
            if ("CLOSED".equalsIgnoreCase(status)) {
                // NFR: Compliance - Audit even for skipped logic
                complianceAuditService.logAuditEvent(claimId, "CLOSED_CLAIM_SKIP_DUPLICATE");
                // NFR: Observability - Structured logging mock verification relies on audit/service calls
                return "TRANSFORMED_CLOSED";
            }

            // Duplicate check logic for non-closed claims
            boolean isDuplicate = duplicateCheckService.checkDuplicate(claimId);
            complianceAuditService.logAuditEvent(claimId, isDuplicate ? "DUPLICATE_DETECTED" : "DUPLICATE_CLEAR");
            
            // NFR: Availability/Operability - Store document reference
            String objectUri = documentStorageService.storeDocument(
                "DocumentStorage-bucket", 
                "DocumentStorage/" + claimId + ".json", 
                payload
            );
            
            return "TRANSFORMED_OPEN";
        }
    }

    @Mock
    private PolicyClaimsDBService policyClaimsDB;
    
    @Mock
    private DuplicateCheckService duplicateCheckService;
    
    @Mock
    private ComplianceAuditService complianceAuditService;
    
    @Mock
    private DocumentStorageService documentStorageService;
    
    private ClaimTransformationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new ClaimTransformationOrchestrator(
            policyClaimsDB,
            duplicateCheckService,
            complianceAuditService,
            documentStorageService
        );
    }

    @Test
    void closed_claims_excluded_from_duplicate_check() {
        // Arrange
        String claimId = "CLM-TEST-001";
        String payload = "{\"claimId\":\"CLM-TEST-001\",\"status\":\"CLOSED\"}";
        
        // Mock DB to return CLOSED status
        when(policyClaimsDB.getClaimStatus(claimId)).thenReturn("CLOSED");
        
        // Mock document storage to return a safe URI
        when(documentStorageService.storeDocument(anyString(), anyString(), anyString()))
            .thenReturn("s3://DocumentStorage-bucket/DocumentStorage/CLM-TEST-001.json");

        // Act
        String result = orchestrator.initiateAndTransform(claimId, payload);

        // Assert
        assertEquals("TRANSFORMED_CLOSED", result, "Claim should be transformed as CLOSED");
        
        // Verify Duplicate Check was NEVER called
        verify(duplicateCheckService, never()).checkDuplicate(anyString());
        
        // Verify Compliance Audit recorded the exclusion
        verify(complianceAuditService, times(1)).logAuditEvent(eq(claimId), eq("CLOSED_CLAIM_SKIP_DUPLICATE"));
        
        // Verify Document Storage was still called (NFR: Operability/Availability)
        verify(documentStorageService, times(1)).storeDocument(
            eq("DocumentStorage-bucket"), 
            eq("DocumentStorage/" + claimId + ".json"), 
            anyString()
        );
    }
}
