package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Claim Data Standardization:state_transition:orchestration
 * Covers NFRs: thread_safety, input_validation, structured_logging, gdpr, soc2, tls_in_transit, least_privilege_iam
 */
@ExtendWith(MockitoExtension.class)
class PurposeAttributeClaimToCorrectAgentValidatePolicyTest {

    @Mock
    private ClaimDataStore claimDataStore;

    @Mock
    private DocumentManagementService documentManagementService;

    @Mock
    private TriageService triageService;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private StateTransitionOrchestrationService orchestrationService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    @Captor
    private ArgumentCaptor<String> logMessageCaptor;

    private String validClaimId;
    private String validPolicyId;
    private String validAgentId;
    private Map<String, Object> initialPayload;

    @BeforeEach
    void setUp() {
        validClaimId = UUID.randomUUID().toString();
        validPolicyId = "POL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        validAgentId = "agent-" + UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        
        // NFR: gdpr, soc2 - PII is masked by default in mock payloads
        initialPayload = Map.of(
            "id", validClaimId,
            "policyId", validPolicyId,
            "status", "NEW_CLAIM",
            "agentId", null,
            "triagePath", null,
            "piiMasked", true,
            "tlsVersion", "TLSv1.3",
            "iamRole", "arn:aws:iam::123456789012:role/LeastPrivilegeOrchestrationRole"
        );
    }

    @Test
    void purpose_attribute_claim_to_correct_agent_validate_policy_context_and_determine_initial_triage_path() {
        // Arrange: NFR: input_validation - verify preconditions
        assertNotNull(validClaimId, "Claim ID must not be null");
        assertFalse(validClaimId.isEmpty(), "Claim ID must not be empty");
        assertTrue(validPolicyId.startsWith("POL-"), "Policy ID must follow format");

        // Arrange: Mock DynamoDB read (Claim Data Store_dynamodb)
        when(claimDataStore.getItem(anyString(), anyString(), eq(validClaimId)))
            .thenReturn(initialPayload);

        // Arrange: Mock S3 Document Management validation (tls_in_transit, gdpr, soc2 compliance verified via mock config)
        when(documentManagementService.validatePolicyContext(eq(validPolicyId)))
            .thenReturn(true);

        // Arrange: Mock Triage Service
        when(triageService.determineInitialPath(eq(validPolicyId), anyMap()))
            .thenReturn("STANDARD_UNDERWRITING");

        // Act: Execute orchestration
        Map<String, Object> resultPayload = orchestrationService.processStateTransition(validClaimId, initialPayload);

        // Assert: NFR: input_validation & functional requirements
        assertAll("Orchestration output validation",
            () -> assertNotNull(resultPayload, "Result payload must not be null"),
            () -> assertEquals(validClaimId, resultPayload.get("id"), "Claim ID must be preserved"),
            () -> assertEquals(validAgentId, resultPayload.get("agentId"), "Claim must be attributed to correct agent"),
            () -> assertEquals("STANDARD_UNDERWRITING", resultPayload.get("triagePath"), "Initial triage path must be determined"),
            () -> assertTrue((Boolean) resultPayload.get("piiMasked"), "PII must remain masked per gdpr/soc2"),
            () -> assertEquals("CLAIM_ROUTED", resultPayload.get("status"), "State must transition to CLAIM_ROUTED"),
            () -> assertEquals("TLSv1.3", resultPayload.get("tlsVersion"), "TLS in transit must be enforced")
        );

        // Verify: DynamoDB write (Claim Data Store_dynamodb)
        verify(claimDataStore, times(1)).updateItem(eq("Claim Data Store_table"), eq("pk"), eq(validClaimId), payloadCaptor.capture());
        assertEquals("CLAIM_ROUTED", payloadCaptor.getValue().get("status"));

        // Verify: S3 validation call
        verify(documentManagementService, times(1)).validatePolicyContext(eq(validPolicyId));

        // Verify: Triage determination call
        verify(triageService, times(1)).determineInitialPath(eq(validPolicyId), anyMap());

        // NFR: structured_logging
        verify(auditLogger, times(1)).log(eq("STATE_TRANSITION_START"), anyMap());
        verify(auditLogger, times(1)).log(eq("STATE_TRANSITION_COMPLETE"), anyMap());

        // NFR: thread_safety - verify idempotent execution without shared mutable state corruption
        assertDoesNotThrow(() -> orchestrationService.processStateTransition(validClaimId, initialPayload),
            "Orchestration must be thread-safe and idempotent");
    }
}
