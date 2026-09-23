package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimValidationDecisionService claimValidationDecisionService;

    @BeforeEach
    void setUp() {
        claimValidationDecisionService = new ClaimValidationDecisionService(documentStoreService, policyValidationService, rulesEngineService);
    }

    @Test
    void full_audit_explainability_context_retained() {
        String claimId = "claim-123";
        Map<String, Object> inputPayload = Map.of(
            "id", claimId,
            "payload", Map.of("claimType", "FNOL", "severity", "HIGH"),
            "auditContext", Map.of("traceId", "trace-abc", "explainabilityScore", 0.95, "decisionReason", "auto_approved")
        );

        when(documentStoreService.read(anyString(), anyString())).thenReturn(Map.of("status", "STORAGE_OK"));
        when(policyValidationService.validate(anyString())).thenReturn(true);
        when(rulesEngineService.evaluate(anyString())).thenReturn(Map.of("ruleResult", "PASS"));

        Map<String, Object> result = claimValidationDecisionService.processClaim(inputPayload);

        assertNotNull(result);
        Map<String, Object> auditContext = (Map<String, Object>) result.get("auditContext");
        assertNotNull(auditContext, "Audit context should be retained");
        assertEquals("auto_approved", auditContext.get("decisionReason"));
        assertEquals(0.95, auditContext.get("explainabilityScore"));
        assertEquals("trace-abc", auditContext.get("traceId"));

        verify(documentStoreService, times(1)).read(anyString(), anyString());
        verify(policyValidationService, times(1)).validate(anyString());
        verify(rulesEngineService, times(1)).evaluate(anyString());
    }

    // Minimal stubs for compilation and mock isolation
    interface DocumentStoreService { Map<String, Object> read(String bucket, String key); }
    interface PolicyValidationService { boolean validate(String claimId); }
    interface RulesEngineService { Map<String, Object> evaluate(String claimId); }
    
    static class ClaimValidationDecisionService {
        private final DocumentStoreService documentStoreService;
        private final PolicyValidationService policyValidationService;
        private final RulesEngineService rulesEngineService;
        
        ClaimValidationDecisionService(DocumentStoreService d, PolicyValidationService p, RulesEngineService r) {
            this.documentStoreService = d;
            this.policyValidationService = p;
            this.rulesEngineService = r;
        }
        
        Map<String, Object> processClaim(Map<String, Object> payload) {
            return Map.of(
                "id", payload.get("id"),
                "payload", payload.get("payload"),
                "auditContext", payload.get("auditContext"),
                "validationStatus", policyValidationService.validate(String.valueOf(payload.get("id"))),
                "ruleEvaluation", rulesEngineService.evaluate(String.valueOf(payload.get("id")))
            );
        }
    }
}
