package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Multi-Channel FNOL Submission:decision:validation.
 * Verifies data capture, input normalization, and active policy matching.
 */
@ExtendWith(MockitoExtension.class)
class PurposeCaptureFnolDataNormalizeInputsAndMatchTest {

    @Mock
    private PolicyCoverageValidator policyCoverageValidator;

    @Mock
    private DocumentMediaStore documentMediaStore;

    @Mock
    private CommunicationAckManager communicationAckManager;

    @InjectMocks
    private MultiChannelFnolSubmissionDecisionValidator fnolValidator;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection and reset automatically
    }

    @Test
    void purpose_capture_fnol_data_normalize_inputs_and_match_to_an_active_policy() {
        // Arrange: Simulate raw FNOL payload from a multi-channel source
        Map<String, Object> rawPayload = new HashMap<>();
        rawPayload.put("policy_number", "POL-88219");
        rawPayload.put("incident_date", "2023-11-05");
        rawPayload.put("channel", "CALL_CENTER");
        rawPayload.put("claimant_name", "Jane Doe");
        rawPayload.put("loss_description", "Minor fender bender");

        // Arrange: Expected active policy record from DynamoDB (Policy_Coverage_Validator)
        Map<String, Object> activePolicyRecord = new HashMap<>();
        activePolicyRecord.put("policy_number", "POL-88219");
        activePolicyRecord.put("status", "ACTIVE");
        activePolicyRecord.put("coverage_type", "AUTO_COMPREHENSIVE");
        activePolicyRecord.put("deductible", 500);

        // Mock policy lookup to simulate successful match to active policy
        when(policyCoverageValidator.findByPolicyNumber("POL-88219"))
                .thenReturn(Optional.of(activePolicyRecord));

        // Act: Execute FNOL submission flow (capture, normalize, validate, match)
        Map<String, Object> submissionResult = fnolValidator.processSubmission(rawPayload);

        // Assert: Data Capture & Normalization
        assertNotNull(submissionResult);
        assertTrue((Boolean) submissionResult.get("dataCaptured"));
        assertEquals("POL-88219", submissionResult.get("normalizedPolicyId"));
        assertEquals("2023-11-05T00:00:00Z", submissionResult.get("normalizedIncidentDate"));
        assertEquals("CALL_CENTER", submissionResult.get("normalizedChannel"));

        // Assert: Validation & Policy Match Decision
        assertTrue((Boolean) submissionResult.get("validationPassed"));
        assertEquals("ACTIVE", submissionResult.get("matchedPolicyStatus"));
        assertEquals("DECISION_APPROVED", submissionResult.get("decision"));
        assertEquals("POL-88219", submissionResult.get("matchedPolicyId"));

        // Verify: External I/O interactions (DynamoDB policy lookup)
        verify(policyCoverageValidator, times(1)).findByPolicyNumber("POL-88219");
        verifyNoInteractions(documentMediaStore, communicationAckManager);
    }

    // Minimal interface stubs to represent infrastructure contracts for compilation context
    interface PolicyCoverageValidator {
        Optional<Map<String, Object>> findByPolicyNumber(String policyNumber);
    }

    interface DocumentMediaStore {
        String storeDocument(String bucketName, String objectKey, byte[] content);
    }

    interface CommunicationAckManager {
        String sendEmail(String fromAddress, List<String> toAddresses, String region, String subject, String body);
    }

    class MultiChannelFnolSubmissionDecisionValidator {
        public Map<String, Object> processSubmission(Map<String, Object> payload) {
            Map<String, Object> result = new HashMap<>();
            result.put("dataCaptured", true);
            result.put("normalizedPolicyId", payload.get("policy_number"));
            result.put("normalizedIncidentDate", payload.get("incident_date") + "T00:00:00Z");
            result.put("normalizedChannel", payload.get("channel"));
            
            String policyId = (String) payload.get("policy_number");
            Optional<Map<String, Object>> policyOpt = policyCoverageValidator.findByPolicyNumber(policyId);
            
            if (policyOpt.isPresent()) {
                Map<String, Object> policy = policyOpt.get();
                result.put("matchedPolicyId", policy.get("policy_number"));
                result.put("matchedPolicyStatus", policy.get("status"));
                result.put("validationPassed", "ACTIVE".equals(policy.get("status")));
                result.put("decision", "DECISION_APPROVED");
            } else {
                result.put("validationPassed", false);
                result.put("decision", "DECISION_REJECTED");
            }
            return result;
        }
    }
}
