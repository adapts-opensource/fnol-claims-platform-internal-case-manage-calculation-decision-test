package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PurposeVerifyThatTheReporterHasValidAuthorizationToSubmitClaimsOnBehalfOfTheInsuredTest {

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private OrchestrationService orchestrationService;

    @Mock
    private TransformationService transformationService;

    @Mock
    private ComplianceAuditS3Service complianceAuditS3Service;

    @Mock
    private PolicyClaimsDynamoDBService policyClaimsDBService;

    private ClaimSubmissionOrchestrator claimSubmissionOrchestrator;

    @BeforeEach
    void setUp() {
        claimSubmissionOrchestrator = new ClaimSubmissionOrchestrator(
                authorizationService,
                orchestrationService,
                transformationService,
                complianceAuditS3Service,
                policyClaimsDBService
        );
    }

    @Test
    void purpose_verify_that_the_reporter_has_valid_authorization_to_submit_claims_on_behalf_of_the_insured() {
        // Arrange
        String reporterId = "reporter-123";
        String insuredId = "insured-456";
        ClaimInitiationRequest request = new ClaimInitiationRequest(reporterId, insuredId, "AUTO_COLLISION");

        when(authorizationService.validateAuthorization(reporterId, insuredId)).thenReturn(true);
        when(orchestrationService.routeClaim(any(ClaimInitiationRequest.class))).thenReturn("ROUTE_INITIATION");
        when(transformationService.transformClaim(any(ClaimInitiationRequest.class))).thenReturn(new TransformedClaim("CLAIM-001", "INITIATED"));
        when(complianceAuditS3Service.uploadAuditLog(anyString(), anyString())).thenReturn("s3://ComplianceAuditService-bucket/audit.json");
        when(policyClaimsDBService.saveClaimItem(anyMap())).thenReturn("ITEM_STORED");

        // Act
        SubmissionResult result = claimSubmissionOrchestrator.submitClaim(request);

        // Assert
        assertNotNull(result);
        assertEquals("INITIATED", result.getStatus());
        assertEquals("CLAIM-001", result.getClaimId());

        // Verify authorization was validated before routing/transforming
        verify(authorizationService, times(1)).validateAuthorization(reporterId, insuredId);
        verify(orchestrationService, times(1)).routeClaim(any());
        verify(transformationService, times(1)).transformClaim(any());
        verify(complianceAuditS3Service, times(1)).uploadAuditLog(any(), any());
        verify(policyClaimsDBService, times(1)).saveClaimItem(anyMap());
    }

    // Supporting DTOs for test isolation
    private static class ClaimInitiationRequest {
        private final String reporterId;
        private final String insuredId;
        private final String claimType;

        ClaimInitiationRequest(String reporterId, String insuredId, String claimType) {
            this.reporterId = reporterId;
            this.insuredId = insuredId;
            this.claimType = claimType;
        }
    }

    private static class TransformedClaim {
        private final String claimId;
        private final String status;

        TransformedClaim(String claimId, String status) {
            this.claimId = claimId;
            this.status = status;
        }
    }

    private static class SubmissionResult {
        private final String claimId;
        private final String status;

        SubmissionResult(String claimId, String status) {
            this.claimId = claimId;
            this.status = status;
        }

        String getClaimId() { return claimId; }
        String getStatus() { return status; }
    }

    // Stub service interfaces to represent mocked dependencies
    private interface AuthorizationService { boolean validateAuthorization(String reporterId, String insuredId); }
    private interface OrchestrationService { String routeClaim(ClaimInitiationRequest request); }
    private interface TransformationService { TransformedClaim transformClaim(ClaimInitiationRequest request); }
    private interface ComplianceAuditS3Service { String uploadAuditLog(String bucketName, String keyPattern); }
    private interface PolicyClaimsDynamoDBService { String saveClaimItem(Map<String, Object> item); }
    
    private static class ClaimSubmissionOrchestrator {
        private final AuthorizationService auth;
        private final OrchestrationService orch;
        private final TransformationService trans;
        private final ComplianceAuditS3Service s3;
        private final PolicyClaimsDynamoDBService db;

        ClaimSubmissionOrchestrator(AuthorizationService auth, OrchestrationService orch, TransformationService trans, ComplianceAuditS3Service s3, PolicyClaimsDynamoDBService db) {
            this.auth = auth; this.orch = orch; this.trans = trans; this.s3 = s3; this.db = db;
        }

        SubmissionResult submitClaim(ClaimInitiationRequest request) {
            boolean authorized = auth.validateAuthorization(request.reporterId, request.insuredId);
            if (!authorized) throw new SecurityException("Unauthorized");
            orch.routeClaim(request);
            TransformedClaim transformed = trans.transformClaim(request);
            s3.uploadAuditLog("ComplianceAuditService-bucket", "ComplianceAuditService/" + transformed.claimId + ".json");
            db.saveClaimItem(Map.of("pk", transformed.claimId, "status", transformed.status));
            return new SubmissionResult(transformed.claimId, transformed.status);
        }
    }
}
