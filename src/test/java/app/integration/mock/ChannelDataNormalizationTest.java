package app.integration.mock;

import app.integration.mock.dto.AgentSubmission;
import app.integration.mock.dto.ClaimResult;
import app.integration.mock.dto.InsuredSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class for ChannelDataNormalization.
 * Verifies that agent-assisted and insured self-service submissions normalize to the same unified claim data model.
 */
class ChannelDataNormalizationTest {

    @Mock
    private PolicyLookupService policyLookupService;

    @Mock
    private RiskAddressNormalizer riskAddressNormalizer;

    @Mock
    private ClaimTypeClassifier claimTypeClassifier;

    @Mock
    private DuplicateCheckService duplicateCheckService;

    @Mock
    private TriageOutcomeService triageOutcomeService;

    @Mock
    private Validator inputValidator;

    @Mock
    private Logger logger;

    @InjectMocks
    private FnolOrchestrationService fnolOrchestrationService;

    private static final String POLICY_NUMBER = "FL-DP3-98765";
    private static final String ADDRESS = "123 Palm Ave";
    private static final String CAUSE = "Fire";
    private static final String TENANT_ID = "newco_insurance_tenant";
    private static final String NORMALIZED_POLICY_ID = "POL-UNIFIED-98765";
    private static final String NORMALIZED_ADDRESS = "123 Palm Ave, Apt 1, Tampa, FL 33602, USA";
    private static final String EXPECTED_CLAIM_TYPE = "Standard property claim";
    private static final String EXPECTED_TRIAGE_OUTCOME = "Approved for immediate triage";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("transform_agent_vs_insured_data_model")
    void transform_agent_vs_insured_data_model() {
        // Arrange: Mock external dependencies
        when(policyLookupService.lookupByPolicyNumber(POLICY_NUMBER)).thenReturn(NORMALIZED_POLICY_ID);
        when(riskAddressNormalizer.normalize(ADDRESS)).thenReturn(NORMALIZED_ADDRESS);
        when(claimTypeClassifier.classify(CAUSE)).thenReturn(EXPECTED_CLAIM_TYPE);
        when(duplicateCheckService.check(anyString())).thenReturn(false);
        when(triageOutcomeService.computeOutcome(any())).thenReturn(EXPECTED_TRIAGE_OUTCOME);
        when(inputValidator.validate(any())).thenReturn(true);
        when(logger.isInfoEnabled()).thenReturn(true);

        // Arrange: Input Data Models
        AgentSubmission agentSubmission = new AgentSubmission(
                POLICY_NUMBER, ADDRESS, CAUSE, "Agent", TENANT_ID
        );
        InsuredSubmission insuredSubmission = new InsuredSubmission(
                POLICY_NUMBER, ADDRESS, CAUSE, "Insured", TENANT_ID
        );

        // Act: Process both channels
        ClaimResult agentResult = fnolOrchestrationService.process(agentSubmission);
        ClaimResult insuredResult = fnolOrchestrationService.process(insuredSubmission);

        // Assert: Normalized policy_id matches across channels
        assertEquals(NORMALIZED_POLICY_ID, agentResult.getPolicyId(), "Policy ID should be normalized");
        assertEquals(NORMALIZED_POLICY_ID, insuredResult.getPolicyId(), "Policy ID should be normalized");

        // Assert: Risk address normalized
        assertEquals(NORMALIZED_ADDRESS, agentResult.getNormalizedAddress(), "Address should be normalized");
        assertEquals(NORMALIZED_ADDRESS, insuredResult.getNormalizedAddress(), "Address should be normalized");

        // Assert: Claim type assigned as Standard property claim
        assertEquals(EXPECTED_CLAIM_TYPE, agentResult.getClaimType(), "Claim type should be classified");
        assertEquals(EXPECTED_CLAIM_TYPE, insuredResult.getClaimType(), "Claim type should be classified");

        // Assert: No duplicate records created
        assertFalse(agentResult.isDuplicate(), "Agent submission should not be duplicate");
        assertFalse(insuredResult.isDuplicate(), "Insured submission should not be duplicate");
        verify(duplicateCheckService, times(2)).check(anyString()); // Called for both channels

        // Assert: Triage outcome identical
        assertEquals(EXPECTED_TRIAGE_OUTCOME, agentResult.getTriageOutcome(), "Triage outcome should match");
        assertEquals(EXPECTED_TRIAGE_OUTCOME, insuredResult.getTriageOutcome(), "Triage outcome should match");

        // Assert: NFR - Idempotency keys enforce thread safety (keys generated based on inputs)
        assertNotNull(agentResult.getIdempotencyKey(), "Idempotency key must be present");
        assertNotNull(insuredResult.getIdempotencyKey(), "Idempotency key must be present");
        // Since inputs are identical except channel metadata, keys should be consistent if channel is excluded from hash
        // or distinct if channel is included. Here we assert presence and format.
        assertTrue(agentResult.getIdempotencyKey().startsWith("IDM_"), "Idempotency key format invalid");

        // Assert: NFR - tenant_id and audit timestamps required
        assertEquals(TENANT_ID, agentResult.getTenantId(), "Tenant ID must be propagated");
        assertEquals(TENANT_ID, insuredResult.getTenantId(), "Tenant ID must be propagated");
        assertNotNull(agentResult.getAuditTimestamp(), "Audit timestamp must be present");
        assertNotNull(insuredResult.getAuditTimestamp(), "Audit timestamp must be present");

        // Assert: NFR - Structured logging for observability
        ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(2)).info(logMessageCaptor.capture(), any());
        String logPayload = logMessageCaptor.getValue();
        assertTrue(logPayload.contains("tenant_id"), "Structured log must contain tenant_id");
        assertTrue(logPayload.contains("claim_id"), "Structured log must contain claim_id");

        // Assert: NFR - Input validation at service boundaries
        verify(inputValidator, times(2)).validate(any());
    }
}
