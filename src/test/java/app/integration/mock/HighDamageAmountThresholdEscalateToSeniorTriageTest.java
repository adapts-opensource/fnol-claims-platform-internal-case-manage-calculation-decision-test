package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationStateTransitionOrchTest {

    @Mock
    private ClaimDataStoreService claimDataStoreService;

    @Mock
    private RulesTriageService rulesTriageService;

    @Mock
    private DocumentManagementService documentManagementService;

    @InjectMocks
    private StateTransitionOrchestrator orchestrator;

    @Test
    void high_damage_amount_threshold_escalate_to_senior_triage() {
        String claimId = "CLM-98765";
        double damageAmount = 150000.0;
        double threshold = 100000.0;
        Map<String, Object> payload = new HashMap<>();
        payload.put("claimId", claimId);
        payload.put("damageAmount", damageAmount);
        payload.put("status", "INITIATED");

        when(rulesTriageService.evaluateRules(payload))
                .thenReturn(Map.of("escalationLevel", "SENIOR", "requiresSeniorReview", true));

        Map<String, Object> result = orchestrator.processStateTransition(claimId, payload);

        assertNotNull(result);
        assertEquals("SENIOR_TRIAGE", result.get("status"));
        assertTrue((Boolean) result.get("requiresSeniorReview"));
        verify(rulesTriageService, times(1)).evaluateRules(payload);
        verify(claimDataStoreService).updateItem("Claim Data Store_table", "pk", claimId, result);
        verify(documentManagementService).writeDocument("Document Management-bucket",
                "Document Management/" + claimId + ".json", result);
    }
}
