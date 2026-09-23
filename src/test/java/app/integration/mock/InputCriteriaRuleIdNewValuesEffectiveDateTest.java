package app.integration.mock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationValidationDecisionTest {

    @Mock
    private DocumentStoreService_s3 documentStoreService;

    @Mock
    private PolicyValidationService_dynamodb policyValidationService;

    @Mock
    private RulesEngineService_dynamodb rulesEngineService;

    private ClaimDataStandardizationService claimDataStandardizationService;

    @BeforeEach
    void setUp() {
        claimDataStandardizationService = new ClaimDataStandardizationService(
            documentStoreService,
            policyValidationService,
            rulesEngineService
        );
    }

    @Test
    void input_criteria_rule_id_new_values_effective_date_approval_workflow_id() {
        // Arrange
        String ruleId = "RULE_123";
        Map<String, Object> newValues = Map.of("coverageType", "COMPREHENSIVE", "deductible", 500);
        LocalDate effectiveDate = LocalDate.of(2024, 1, 15);
        String approvalWorkflowId = "WF_456";
        String claimId = UUID.randomUUID().toString();

        when(documentStoreService.getObject(any(), any())).thenReturn(Map.of("claimId", claimId));
        when(policyValidationService.validateClaim(any(), any())).thenReturn(true);
        when(rulesEngineService.evaluateRules(any(), any(), any())).thenReturn(Map.of("decision", "APPROVED"));

        // Act
        Map<String, Object> result = claimDataStandardizationService.processClaimData(
            claimId, newValues, effectiveDate, approvalWorkflowId
        );

        // Assert
        assertNotNull(result);
        assertEquals("APPROVED", result.get("decision"));
        assertEquals(ruleId, result.get("appliedRuleId"));
        assertEquals(effectiveDate.toString(), result.get("effectiveDate"));
        assertEquals(approvalWorkflowId, result.get("approvalWorkflowId"));

        verify(documentStoreService).getObject(any(), any());
        verify(policyValidationService).validateClaim(any(), any());
        verify(rulesEngineService).evaluateRules(any(), any(), any());
    }
}
