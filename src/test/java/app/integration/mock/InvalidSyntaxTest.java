package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionEnrichmentInvalidSyntaxTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @Test
    void invalid_syntax() {
        String malformedPayload = "{\"claimId\": \"CLM-001\", \"data\": <invalid_json_syntax>";
        String claimId = "CLM-001";

        when(rulesEngineDecisionService.getItem(anyString(), anyString())).thenReturn(java.util.Map.of("decision", "PENDING"));

        assertThrows(IllegalArgumentException.class, () -> {
            ClaimDataEnrichmentService service = new ClaimDataEnrichmentService(auditDiaryStore, rulesEngineDecisionService, workflowTaskRouter);
            service.processEnrichment(claimId, malformedPayload);
        });

        verify(rulesEngineDecisionService, times(1)).getItem(anyString(), anyString());
        verify(auditDiaryStore, times(1)).writeAudit(eq("INPUT_VALIDATION_FAILURE"), anyString());
    }
}
