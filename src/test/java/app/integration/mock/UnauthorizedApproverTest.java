package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClaimDataStandardizationDecisionEnrichmentUnauthorizedApproverTest {

    @Mock
    private RulesEngineDecisionService rulesEngine;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    private ClaimEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        enrichmentService = new ClaimEnrichmentService(rulesEngine, auditDiaryStore);
    }

    @Test
    void unauthorized_approver() {
        String claimId = "CLM-789-UNAUTH";
        String approverId = "APPR-GUEST";
        Map<String, Object> payload = Map.of("claimId", claimId, "approverId", approverId);

        when(rulesEngine.evaluateDecision(eq(claimId), anyMap())).thenReturn(false);

        assertThrows(UnauthorizedApproverException.class, () -> {
            enrichmentService.processEnrichment(payload);
        });

        verify(auditDiaryStore).writeAuditRecord(eq(claimId), anyMap());
    }

    interface RulesEngineDecisionService {
        boolean evaluateDecision(String claimId, Map<String, Object> payload);
    }

    interface AuditDiaryStore {
        void writeAuditRecord(String claimId, Map<String, Object> auditData);
    }

    static class UnauthorizedApproverException extends RuntimeException {
        UnauthorizedApproverException(String message) { super(message); }
    }

    static class ClaimEnrichmentService {
        private final RulesEngineDecisionService rulesEngine;
        private final AuditDiaryStore auditDiaryStore;

        ClaimEnrichmentService(RulesEngineDecisionService rulesEngine, AuditDiaryStore auditDiaryStore) {
            this.rulesEngine = rulesEngine;
            this.auditDiaryStore = auditDiaryStore;
        }

        void processEnrichment(Map<String, Object> payload) {
            String claimId = (String) payload.get("claimId");
            boolean isAuthorized = rulesEngine.evaluateDecision(claimId, payload);
            auditDiaryStore.writeAuditRecord(claimId, Map.of("authorized", isAuthorized));
            if (!isAuthorized) {
                throw new UnauthorizedApproverException("Unauthorized approver detected for claim: " + claimId);
            }
        }
    }
}
