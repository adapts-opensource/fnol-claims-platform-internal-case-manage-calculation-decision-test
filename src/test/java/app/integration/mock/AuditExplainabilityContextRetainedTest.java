package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditExplainabilityContextRetainedTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimDecisionService claimDecisionService;

    @BeforeEach
    void setUp() {
        claimDecisionService = new ClaimDecisionService(documentStoreService, policyValidationService, rulesEngineService);
    }

    @Test
    void audit_explainability_context_retained() {
        // Given
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("id", claimId);
        inputPayload.put("claimType", "AUTO");
        inputPayload.put("amount", 1500.0);

        Map<String, Object> expectedAuditContext = Map.of(
            "auditId", UUID.randomUUID().toString(),
            "decisionReason", "APPROVED",
            "traceId", UUID.randomUUID().toString(),
            "timestamp", System.currentTimeMillis(),
            "explanation", "Standard validation rules passed"
        );

        when(policyValidationService.validate(anyString(), anyMap())).thenReturn(expectedAuditContext);
        when(rulesEngineService.evaluate(anyString(), anyMap())).thenReturn(expectedAuditContext);

        // When
        Map<String, Object> result = claimDecisionService.processClaim(inputPayload);

        // Then
        assertNotNull(result, "Result payload must not be null");
        assertEquals(claimId, result.get("id"), "Claim ID must be preserved");

        // Verify audit & explainability context is retained
        assertTrue(result.containsKey("auditContext"), "Payload must contain audit context for compliance & observability");
        @SuppressWarnings("unchecked")
        Map<String, Object> retainedAudit = (Map<String, Object>) result.get("auditContext");
        assertNotNull(retainedAudit);
        assertTrue(retainedAudit.containsKey("decisionReason"), "Decision reason required for explainability");
        assertTrue(retainedAudit.containsKey("traceId"), "Trace ID required for audit trail");
        assertEquals("APPROVED", retainedAudit.get("decisionReason"));
        assertEquals("Standard validation rules passed", retainedAudit.get("explanation"));
    }

    // Minimal infrastructure contract stubs for mocking
    interface DocumentStoreService {
        String storeDocument(String bucketName, String keyPattern, Map<String, Object> payload);
    }

    interface PolicyValidationService {
        Map<String, Object> validate(String tableName, Map<String, Object> itemPayload);
    }

    interface RulesEngineService {
        Map<String, Object> evaluate(String tableName, Map<String, Object> itemPayload);
    }

    class ClaimDecisionService {
        private final DocumentStoreService documentStoreService;
        private final PolicyValidationService policyValidationService;
        private final RulesEngineService rulesEngineService;

        ClaimDecisionService(DocumentStoreService documentStoreService, PolicyValidationService policyValidationService, RulesEngineService rulesEngineService) {
            this.documentStoreService = documentStoreService;
            this.policyValidationService = policyValidationService;
            this.rulesEngineService = rulesEngineService;
        }

        Map<String, Object> processClaim(Map<String, Object> inputPayload) {
            String claimId = (String) inputPayload.get("id");
            Map<String, Object> validationContext = policyValidationService.validate("PolicyValidationService_table", inputPayload);
            Map<String, Object> ruleContext = rulesEngineService.evaluate("RulesEngineService_table", inputPayload);
            
            Map<String, Object> result = new HashMap<>(inputPayload);
            // Retain audit & explainability context per compliance & observability NFRs
            result.put("auditContext", validationContext);
            return result;
        }
    }
}
