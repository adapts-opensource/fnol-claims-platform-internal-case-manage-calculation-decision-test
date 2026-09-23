package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingOrchestrationTransformationMockTest {

    @Mock
    private TransformationEngine transformationEngine;

    @Mock
    private PolicyClaimsDbClient policyClaimsDbClient;

    @Mock
    private ComplianceAuditService complianceAuditService;

    @Mock
    private DocumentStorageService documentStorageService;

    private ClaimInitiationOrchestrator claimInitiationOrchestrator;

    @BeforeEach
    void setUp() {
        claimInitiationOrchestrator = new ClaimInitiationOrchestrator(
            transformationEngine, policyClaimsDbClient, complianceAuditService, documentStorageService
        );
    }

    @Test
    void applies_when_api_submission_includes_reporter_identity_insured_identity() {
        // Arrange
        String reporterIdentity = "reporter-id-001";
        String insuredIdentity = "insured-id-002";
        Map<String, Object> apiSubmission = Map.of(
            "reporterIdentity", reporterIdentity,
            "insuredIdentity", insuredIdentity,
            "claimType", "AUTO_COLLISION"
        );

        Map<String, Object> expectedTransformedPayload = Map.of(
            "reporterId", reporterIdentity,
            "insuredId", insuredIdentity,
            "routingStatus", "ROUTED_TO_ADJUSTER",
            "claimId", "CLM-98765"
        );

        when(transformationEngine.transform(anyMap())).thenReturn(expectedTransformedPayload);
        when(policyClaimsDbClient.putItem(anyMap())).thenReturn("CLM-98765");
        when(complianceAuditService.writeToS3(anyString(), anyString())).thenReturn("s3://ComplianceAuditService-bucket/ComplianceAuditService/CLM-98765.json");
        when(documentStorageService.writeToS3(anyString(), anyString())).thenReturn("s3://DocumentStorage-bucket/DocumentStorage/CLM-98765.json");

        // Act
        var result = claimInitiationOrchestrator.processSubmission(apiSubmission);

        // Assert
        assertNotNull(result);
        assertEquals("CLM-98765", result.claimId());
        assertEquals(reporterIdentity, result.reporterId());
        assertEquals(insuredIdentity, result.insuredId());
        assertEquals("ROUTED_TO_ADJUSTER", result.routingStatus());

        // Verify orchestration & transformation steps
        verify(transformationEngine).transform(apiSubmission);
        verify(policyClaimsDbClient).putItem(expectedTransformedPayload);
        verify(complianceAuditService).writeToS3("ComplianceAuditService-bucket", "ComplianceAuditService/CLM-98765.json");
        verify(documentStorageService).writeToS3("DocumentStorage-bucket", "DocumentStorage/CLM-98765.json");
        verifyNoMoreInteractions(transformationEngine, policyClaimsDbClient, complianceAuditService, documentStorageService);
    }

    // Internal record for test result mapping
    record ProcessingResult(String claimId, String reporterId, String insuredId, String routingStatus) {}

    // Minimal service implementations for compilation context
    static class ClaimInitiationOrchestrator {
        private final TransformationEngine transformationEngine;
        private final PolicyClaimsDbClient policyClaimsDbClient;
        private final ComplianceAuditService complianceAuditService;
        private final DocumentStorageService documentStorageService;

        ClaimInitiationOrchestrator(TransformationEngine transformationEngine, PolicyClaimsDbClient policyClaimsDbClient,
                                    ComplianceAuditService complianceAuditService, DocumentStorageService documentStorageService) {
            this.transformationEngine = transformationEngine;
            this.policyClaimsDbClient = policyClaimsDbClient;
            this.complianceAuditService = complianceAuditService;
            this.documentStorageService = documentStorageService;
        }

        ProcessingResult processSubmission(Map<String, Object> submission) {
            Map<String, Object> transformed = transformationEngine.transform(submission);
            String claimId = policyClaimsDbClient.putItem(transformed);
            complianceAuditService.writeToS3("ComplianceAuditService-bucket", "ComplianceAuditService/" + claimId + ".json");
            documentStorageService.writeToS3("DocumentStorage-bucket", "DocumentStorage/" + claimId + ".json");
            return new ProcessingResult(claimId, (String) transformed.get("reporterId"), (String) transformed.get("insuredId"), (String) transformed.get("routingStatus"));
        }
    }

    interface TransformationEngine { Map<String, Object> transform(Map<String, Object> payload); }
    interface PolicyClaimsDbClient { String putItem(Map<String, Object> item); }
    interface ComplianceAuditService { String writeToS3(String bucket, String key); }
    interface DocumentStorageService { String writeToS3(String bucket, String key); }
}
