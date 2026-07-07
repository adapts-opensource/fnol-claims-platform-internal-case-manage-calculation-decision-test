package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionTransformationTest {

    @Mock
    private PolicyMatchingService policyMatchingService;
    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;
    @Mock
    private AuditDiaryStoreService auditDiaryStoreService;

    private ClaimDataStandardizationDecisionTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new ClaimDataStandardizationDecisionTransformer(
            policyMatchingService,
            rulesEngineDecisionService,
            auditDiaryStoreService
        );
    }

    @Test
    void rule_if_no_policy_matches_create_shell_unmatched_fnol_n() {
        String claimId = "CLM-UNMATCHED-789";
        Map<String, Object> inputPayload = Map.of(
            "id", claimId,
            "eventType", "FNOL",
            "policyNumber", "POL-INVALID-000",
            "submissionTimestamp", "2024-05-20T10:30:00Z"
        );

        when(policyMatchingService.findMatchingPolicy(anyString())).thenReturn(null);

        Map<String, Object> transformedPayload = transformer.transform(inputPayload);

        assertNotNull(transformedPayload, "Transformed payload must not be null");
        assertEquals("Unmatched FNOL", transformedPayload.get("shellType"));
        assertEquals(claimId, transformedPayload.get("id"));
        assertEquals("UNMATCHED", transformedPayload.get("standardizedStatus"));
        assertTrue(((Map<?, ?>) transformedPayload.get("payload")).containsKey("id"));

        verify(rulesEngineDecisionService).persistDecision(
            eq(claimId),
            argThat(item -> "NO_POLICY_MATCH".equals(item.get("ruleOutcome")))
        );
        verify(auditDiaryStoreService).storeAuditLog(
            eq(claimId),
            any(Map.class)
        );
    }
}
