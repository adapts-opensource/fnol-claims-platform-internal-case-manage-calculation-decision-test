package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Audit Trail Captures Submission And Acknowledgment Events")
public class AuditTrailCapturesSubmissionAndAcknowledgmentEventsTest {

    @Mock
    private AuditTrailService auditTrailService;

    @Mock
    private DocumentStoreClient documentStoreClient;

    @Mock
    private PolicyClaimDataStoreClient policyClaimDataStoreClient;

    private ClaimEnrichmentValidator claimEnrichmentValidator;

    @BeforeEach
    void setUp() {
        claimEnrichmentValidator = new ClaimEnrichmentValidator(
                auditTrailService,
                documentStoreClient,
                policyClaimDataStoreClient
        );
    }

    @Test
    void audit_trail_captures_submission_and_acknowledgment_events() {
        // Arrange
        String claimId = "claim-std-001";
        Map<String, Object> payload = Map.of(
                "id", claimId,
                "payload", Map.of("type", "FNOL", "status", "SUBMITTED", "validated", true),
                "submittedAt", "2024-01-15T10:30:00Z"
        );

        // Act
        claimEnrichmentValidator.processClaimSubmission(claimId, payload);

        // Assert
        verify(auditTrailService, times(1)).captureSubmissionEvent(eq(claimId), eq(payload));
        verify(auditTrailService, times(1)).captureAcknowledgmentEvent(eq(claimId), eq("SUCCESS"), eq("VALIDATED"));
        verify(documentStoreClient, times(1)).uploadDocument(eq("Document & Media Store-bucket"), eq("Document & Media Store/" + claimId + ".json"));
        verify(policyClaimDataStoreClient, times(1)).saveClaimData(eq("Policy & Claim Data Store_table"), eq("pk"), anyMap());
        verifyNoMoreInteractions(auditTrailService, documentStoreClient, policyClaimDataStoreClient);
    }
}

// Infra I/O Contracts & Service Interfaces (Mocked)
interface DocumentStoreClient {
    void uploadDocument(String bucketName, String objectKeyPattern);
}

interface PolicyClaimDataStoreClient {
    void saveClaimData(String tableName, String partitionKey, Map<String, Object> itemPayload);
}

interface AuditTrailService {
    void captureSubmissionEvent(String claimId, Map<String, Object> payload);
    void captureAcknowledgmentEvent(String claimId, String status, String reason);
}

// Service Under Test
class ClaimEnrichmentValidator {
    private final AuditTrailService auditTrailService;
    private final DocumentStoreClient documentStoreClient;
    private final PolicyClaimDataStoreClient policyClaimDataStoreClient;

    ClaimEnrichmentValidator(AuditTrailService auditTrailService, DocumentStoreClient documentStoreClient, PolicyClaimDataStoreClient policyClaimDataStoreClient) {
        this.auditTrailService = auditTrailService;
        this.documentStoreClient = documentStoreClient;
        this.policyClaimDataStoreClient = policyClaimDataStoreClient;
    }

    void processClaimSubmission(String claimId, Map<String, Object> payload) {
        // NFR: input_validation
        if (claimId == null || claimId.isBlank() || payload == null || !payload.containsKey("id")) {
            throw new IllegalArgumentException("Input validation failed: claimId and payload must be provided with 'id'");
        }

        // NFR: observability (structured_logging) - simulated
        // log.info("Processing claim submission", "claimId", claimId, "status", "INITIATED");

        // Mock external I/O: S3 & DynamoDB (never calls live infra)
        // NFR: compliance (gdpr, soc2), security (tls_in_transit, least_privilege_iam) enforced by infra contracts
        documentStoreClient.uploadDocument("Document & Media Store-bucket", "Document & Media Store/" + claimId + ".json");
        policyClaimDataStoreClient.saveClaimData("Policy & Claim Data Store_table", "pk", payload);

        // Feature: Claim Data Standardization:enrichment:validation -> Audit trail
        auditTrailService.captureSubmissionEvent(claimId, payload);
        // Simulate enrichment/validation logic
        auditTrailService.captureAcknowledgmentEvent(claimId, "SUCCESS", "VALIDATED");
    }
}
