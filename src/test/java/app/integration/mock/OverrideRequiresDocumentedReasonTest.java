package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationEnrichmentOverrideTest {

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    @Mock
    private AuditDiaryStore auditStore;

    @InjectMocks
    private EnrichmentDecisionService enrichmentService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock injection; explicit setup is minimal
    }

    @Test
    void override_requires_documented_reason() {
        String claimId = "CLM-12345";
        Map<String, Object> claimPayload = Map.of("id", claimId, "status", "PENDING");
        Map<String, Object> overrideRequest = Map.of("claimId", claimId, "action", "OVERRIDE");

        when(rulesEngineService.fetchDecision(claimId)).thenReturn(Map.of("ruleSet", "v1"));

        assertThrows(IllegalArgumentException.class, () -> enrichmentService.processOverride(claimPayload, overrideRequest));
        verify(rulesEngineService).fetchDecision(claimId);
        verifyNoInteractions(auditStore);
    }

    // Infrastructure contract mocks
    interface RulesEngineDecisionService {
        Map<String, Object> fetchDecision(String claimId);
    }

    interface AuditDiaryStore {
        void logAudit(String claimId, String action);
    }

    // Service under test
    static class EnrichmentDecisionService {
        private final RulesEngineDecisionService rulesEngineService;
        private final AuditDiaryStore auditStore;

        EnrichmentDecisionService(RulesEngineDecisionService rulesEngineService, AuditDiaryStore auditStore) {
            this.rulesEngineService = rulesEngineService;
            this.auditStore = auditStore;
        }

        void processOverride(Map<String, Object> claimPayload, Map<String, Object> overrideRequest) {
            String claimId = (String) claimPayload.get("id");
            if (rulesEngineService.fetchDecision(claimId) != null) {
                if (!"OVERRIDE".equals(overrideRequest.get("action"))) {
                    return;
                }
                if (overrideRequest.get("documentedReason") == null || ((String) overrideRequest.get("documentedReason")).isBlank()) {
                    throw new IllegalArgumentException("Override requires documented reason");
                }
                auditStore.logAudit(claimId, "OVERRIDE");
            }
        }
    }
}
