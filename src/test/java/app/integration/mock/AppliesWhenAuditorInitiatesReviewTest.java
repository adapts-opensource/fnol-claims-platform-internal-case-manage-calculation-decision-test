package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimValidationDecisionService decisionService;

    @Test
    void applies_when_auditor_initiates_review() {
        // Arrange: Simulate auditor-initiated review payload with sanitized PII
        // GDPR/SOC2: PII masked per least_privilege_iam & input_validation NFRs
        String claimId = "CLM-AUD-789";
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "initiator", "AUDITOR",
            "reviewStage", "INITIATED",
            "piiData", "REDACTED"
        );

        when(documentStoreService.fetchObject(anyString(), anyString())).thenReturn(payload);
        when(policyValidationService.validatePolicy(anyString())).thenReturn(Map.of("status", "ACTIVE"));
        when(rulesEngineService.evaluateRules(anyString(), anyMap())).thenReturn(Map.of("decision", "APPLY_STANDARDIZATION"));

        // Act: Invoke decision logic (stateless & thread-safe)
        String result = decisionService.evaluateDecision(claimId, payload);

        // Assert: Verify decision applies when auditor initiates review
        assertEquals("APPLY_STANDARDIZATION", result);
        verify(rulesEngineService).evaluateRules(eq(claimId), anyMap());
        verifyNoMoreInteractions(documentStoreService, policyValidationService, rulesEngineService);
    }
}
