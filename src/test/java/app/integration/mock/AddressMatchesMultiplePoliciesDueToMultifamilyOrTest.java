package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

public class ClaimDataStandardizationDecisionTransformationTest {

    @InjectMocks
    private ClaimDataStandardizationDecisionTransformer transformer;

    @Mock
    private RulesEngineDecisionService rulesEngine;

    @Mock
    private WorkflowTaskRouter taskRouter;

    @Mock
    private AuditDiaryStore auditStore;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void address_matches_multiple_policies_due_to_multifamily_or_condo_association() {
        // Arrange
        String claimId = "CLM-2024-MFA-001";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("addressLine1", "1200 Lakeview Dr");
        inputPayload.put("addressLine2", "Unit 4B");
        inputPayload.put("propertyType", "MULTIFAMILY");

        Map<String, Object> rulesResponse = new HashMap<>();
        rulesResponse.put("matchedPolicyCount", 4);
        rulesResponse.put("associationCategory", "CONDO_ASSOCIATION");
        rulesResponse.put("riskFlag", "MULTI_POLICY_OVERLAP");

        when(rulesEngine.evaluateDecision(anyMap())).thenReturn(rulesResponse);

        Map<String, Object> expectedTransformedPayload = new HashMap<>();
        expectedTransformedPayload.put("standardizedAddress", "1200 Lakeview Dr");
        expectedTransformedPayload.put("unitDesignator", "Unit 4B");
        expectedTransformedPayload.put("propertyClassification", "MULTIFAMILY_MULTIPLE_POLICIES");
        expectedTransformedPayload.put("associationType", "CONDO_ASSOCIATION");
        expectedTransformedPayload.put("policyOverlapCount", 4);
        expectedTransformedPayload.put("routingPriority", "HIGH");
        expectedTransformedPayload.put("auditTag", "CLM-2024-MFA-001-MFA");

        // Act
        Map<String, Object> result = transformer.transformClaimData(claimId, inputPayload);

        // Assert
        assertNotNull(result);
        assertEquals(claimId, result.get("id"));
        Map<String, Object> actualPayload = (Map<String, Object>) result.get("payload");
        assertEquals("MULTIFAMILY_MULTIPLE_POLICIES", actualPayload.get("propertyClassification"));
        assertEquals("CONDO_ASSOCIATION", actualPayload.get("associationType"));
        assertEquals(4, actualPayload.get("policyOverlapCount"));
        assertEquals("HIGH", actualPayload.get("routingPriority"));

        verify(rulesEngine, times(1)).evaluateDecision(anyMap());
        verify(taskRouter, times(1)).routeToSpecializedQueue(anyMap());
        verify(auditStore, times(1)).writeAuditEntry(anyString(), anyString(), eq(expectedTransformedPayload));
    }
}
