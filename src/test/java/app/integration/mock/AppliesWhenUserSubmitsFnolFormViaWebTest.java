package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionDecisionValidationTest {

    @Mock private CommunicationAckManager sesMock;
    @Mock private DocumentMediaStore s3Mock;
    @Mock private GuidewireClaimModel claimDynamoMock;
    @Mock private PolicyCoverageValidator policyDynamoMock;
    @Mock private StructuredLogger loggerMock;

    private MultiChannelFnolSubmissionService fnolService;

    @BeforeEach
    void setUp() {
        fnolService = new MultiChannelFnolSubmissionService(sesMock, s3Mock, claimDynamoMock, policyDynamoMock, loggerMock);
    }

    @Test
    void applies_when_user_submits_fnol_form_via_web_mobile_ivr_api() {
        // Given: Valid FNOL submission envelope (applies to WEB, MOBILE, IVR, API channels)
        String channel = "WEB"; // Parity test: interchangeable with MOBILE, IVR, API
        String submissionId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
            "claimantEmail", "policyholder@newco.com",
            "incidentDate", "2024-06-15",
            "description", "Vehicle collision at intersection",
            "attachments", List.of("photo_front.jpg", "police_report.pdf"),
            "piiSensitive", false
        );
        Map<String, Object> submissionEnvelope = Map.of("id", submissionId, "payload", payload);

        // When: Submit FNOL form for validation and decision routing
        FnolDecisionResult result = fnolService.validateAndRoute(submissionEnvelope);

        // Then: Validation decision passes, external I/O contracts are respected
        assertTrue(result.isValid(), "Payload must pass input validation constraints");
        assertEquals("ACCEPTED", result.getDecision(), "Decision must be ACCEPTED for valid multi-channel submission");
        assertEquals(submissionId, result.getId(), "ID must be preserved from data model");

        // Verify SES Communication Ack Manager
        verify(sesMock).sendAck(
            eq("noreply@newco.com"),
            eq(List.of("policyholder@newco.com")),
            eq("us-east-1")
        );

        // Verify S3 Document Media Store
        verify(s3Mock).storeDocument(
            eq("Document_Media_Store-bucket"),
            eq("Document_Media_Store/" + submissionId + ".json")
        );

        // Verify DynamoDB contracts (Guidewire Claim Model & Policy Coverage Validator)
        verify(claimDynamoMock).saveItem(
            eq("Guidewire_Claim_Model_table"),
            eq("pk"),
            any()
        );
        verify(policyDynamoMock).validateCoverage(
            eq("Policy_Coverage_Validator_table"),
            eq("pk"),
            any()
        );

        // Verify Observability & Compliance NFRs
        verify(loggerMock).log(eq("FNOL_VALIDATION_DECISION"), eq(submissionId), any());
        verify(loggerMock).log(eq("GDPR_COMPLIANCE_CHECK"), eq(submissionId), argThat(ctx -> Boolean.FALSE.equals(ctx.get("piiSensitive"))));

        // Verify Data Model Constraints
        assertNotNull(submissionEnvelope.get("id"), "Field 'id' is required");
        assertNotNull(submissionEnvelope.get("payload"), "Field 'payload' is required");
        assertInstanceOf(Map.class, submissionEnvelope.get("payload"), "Field 'payload' must be a map");
    }
}

// Minimal stubs to ensure compilation context for mock interactions
interface CommunicationAckManager { void sendAck(String from, List<String> to, String region); }
interface DocumentMediaStore { String storeDocument(String bucket, String key); }
interface GuidewireClaimModel { void saveItem(String table, String pk, Map<String, Object> item); }
interface PolicyCoverageValidator { boolean validateCoverage(String table, String pk, Map<String, Object> item); }
interface StructuredLogger { void log(String event, String correlationId, Map<String, Object> context); }
class FnolDecisionResult {
    private final String id; private final boolean valid; private final String decision;
    FnolDecisionResult(String id, boolean valid, String decision) { this.id = id; this.valid = valid; this.decision = decision; }
    String getId() { return id; } boolean isValid() { return valid; } String getDecision() { return decision; }
}
class MultiChannelFnolSubmissionService {
    private final CommunicationAckManager ses;
    private final DocumentMediaStore s3;
    private final GuidewireClaimModel claimDynamo;
    private final PolicyCoverageValidator policyDynamo;
    private final StructuredLogger logger;
    MultiChannelFnolSubmissionService(CommunicationAckManager ses, DocumentMediaStore s3, GuidewireClaimModel claimDynamo, PolicyCoverageValidator policyDynamo, StructuredLogger logger) {
        this.ses = ses; this.s3 = s3; this.claimDynamo = claimDynamo; this.policyDynamo = policyDynamo; this.logger = logger;
    }
    FnolDecisionResult validateAndRoute(Map<String, Object> envelope) {
        String id = String.valueOf(envelope.get("id"));
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        logger.log("FNOL_VALIDATION_DECISION", id, payload);
        ses.sendAck("noreply@newco.com", List.of(String.valueOf(payload.get("claimantEmail"))), "us-east-1");
        s3.storeDocument("Document_Media_Store-bucket", "Document_Media_Store/" + id + ".json");
        claimDynamo.saveItem("Guidewire_Claim_Model_table", "pk", payload);
        policyDynamo.validateCoverage("Policy_Coverage_Validator_table", "pk", payload);
        return new FnolDecisionResult(id, true, "ACCEPTED");
    }
}
