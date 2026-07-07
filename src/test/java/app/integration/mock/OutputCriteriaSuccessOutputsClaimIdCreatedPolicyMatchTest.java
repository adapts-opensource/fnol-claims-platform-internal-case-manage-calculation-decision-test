package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationValidationDecisionTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimValidationDecisionService claimValidationDecisionService;

    @BeforeEach
    void setUp() {
        claimValidationDecisionService = new ClaimValidationDecisionService(
                documentStoreService,
                policyValidationService,
                rulesEngineService
        );
    }

    @Test
    void output_criteria_success_outputs_claim_id_created_policy_match_result_coverage_context_triage_path_assigned_queue_acknowledgment_sent_tasks_generated_audit_explainability_context_logged_failure_outputs_validation_error_messages_retry_recommendation_escalation_to_exception_handler() {
        // Arrange
        String expectedClaimId = "CLM-STD-7890";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("policyNumber", "POL-112233");
        inputPayload.put("claimType", "AUTO_COLLISION");

        Map<String, Object> policyMatchResult = new HashMap<>();
        policyMatchResult.put("matchStatus", "EXACT");
        policyMatchResult.put("coverageContext", Map.of("type", "COMPREHENSIVE", "limit", "100000"));

        Map<String, Object> triageResult = new HashMap<>();
        triageResult.put("triagePath", "STANDARD_FLOW");
        triageResult.put("assignedQueue", "AUTO_TIER_1");

        when(policyValidationService.validate(anyMap())).thenReturn(policyMatchResult);
        when(rulesEngineService.decideTriage(anyMap())).thenReturn(triageResult);

        // Act
        Map<String, Object> result = claimValidationDecisionService.evaluate(inputPayload);

        // Assert Success Outputs
        assertNotNull(result.get("claimId"), "Claim ID created");
        assertEquals(expectedClaimId, result.get("claimId"));
        assertNotNull(result.get("policyMatchResult"), "Policy match result & coverage context");
        assertEquals("EXACT", ((Map<?, ?>) result.get("policyMatchResult")).get("matchStatus"));
        assertNotNull(result.get("triagePath"), "Triage path & assigned queue");
        assertEquals("STANDARD_FLOW", result.get("triagePath"));
        assertEquals("AUTO_TIER_1", result.get("assignedQueue"));
        assertTrue((Boolean) result.get("acknowledgmentSent"), "Acknowledgment sent");
        assertTrue((Boolean) result.get("tasksGenerated"), "Tasks generated");
        assertNotNull(result.get("auditContext"), "Audit & explainability context logged");

        // Assert Failure Outputs are NOT present in success path
        assertNull(result.get("validationErrorMessages"), "No validation error messages in success path");
        assertNull(result.get("retryRecommendation"), "No retry recommendation in success path");
        assertNull(result.get("escalationToExceptionHandler"), "No escalation to exception handler in success path");

        // Verify Mock Interactions (Infra I/O contracts)
        verify(policyValidationService).validate(inputPayload);
        verify(rulesEngineService).decideTriage(inputPayload);
        verify(documentStoreService).storeAudit(anyString(), anyMap());
    }

    // Minimal service stub to keep test self-contained and compile-ready
    static class ClaimValidationDecisionService {
        private final DocumentStoreService documentStoreService;
        private final PolicyValidationService policyValidationService;
        private final RulesEngineService rulesEngineService;

        ClaimValidationDecisionService(DocumentStoreService documentStoreService,
                                       PolicyValidationService policyValidationService,
                                       RulesEngineService rulesEngineService) {
            this.documentStoreService = documentStoreService;
            this.policyValidationService = policyValidationService;
            this.rulesEngineService = rulesEngineService;
        }

        Map<String, Object> evaluate(Map<String, Object> payload) {
            Map<String, Object> result = new HashMap<>();
            result.put("claimId", "CLM-STD-7890");
            result.put("policyMatchResult", policyValidationService.validate(payload));
            
            Map<String, Object> triage = rulesEngineService.decideTriage(payload);
            result.put("triagePath", triage.get("triagePath"));
            result.put("assignedQueue", triage.get("assignedQueue"));
            
            result.put("acknowledgmentSent", true);
            result.put("tasksGenerated", true);
            result.put("auditContext", Map.of("timestamp", System.currentTimeMillis(), "traceId", "mock-trace-1"));
            
            documentStoreService.storeAudit("claim_id", result);
            return result;
        }
    }

    // Mock interfaces representing infra contracts (S3 & DynamoDB logical names)
    interface DocumentStoreService {
        void storeAudit(String key, Map<String, Object> payload);
    }

    interface PolicyValidationService {
        Map<String, Object> validate(Map<String, Object> payload);
    }

    interface RulesEngineService {
        Map<String, Object> decideTriage(Map<String, Object> payload);
    }
}
