package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationDecisionTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimValidationDecisionService claimValidationDecisionService;

    @Test
    void if_moratorium_active_dol_falls_within_flag_for_compliance_review() {
        // Arrange
        String claimId = "CLM-2023-001";
        Map<String, Object> payload = Map.of(
                "moratoriumActive", true,
                "dateOfLoss", "2023-11-15",
                "flagPeriodStart", "2023-10-01",
                "flagPeriodEnd", "2023-12-31",
                "policyStatus", "ACTIVE"
        );

        when(policyValidationService.validatePolicy(anyString())).thenReturn(Map.of("status", "ACTIVE", "coverageType", "AUTO"));
        when(rulesEngineService.getDecisionRules(anyString())).thenReturn(Map.of("ruleId", "R-101", "action", "FLAG_COMPLIANCE"));

        // Act
        Map<String, Object> result = claimValidationDecisionService.processDecision(claimId, payload);

        // Assert
        assertEquals("FLAG_COMPLIANCE", result.get("decision"));
        assertTrue((Boolean) result.get("complianceReviewRequired"));
        assertEquals("CLM-2023-001", result.get("claimId"));
        assertNotNull(result.get("standardizedPayload"));

        // Verify external I/O contracts were invoked correctly
        verify(policyValidationService).validatePolicy(claimId);
        verify(rulesEngineService).getDecisionRules(claimId);
        verify(documentStoreService).store(eq("DocumentStoreService-bucket"), eq("claims/CLM-2023-001.json"), any(Map.class));
    }
}
