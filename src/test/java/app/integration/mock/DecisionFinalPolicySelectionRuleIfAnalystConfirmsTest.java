package app.integration.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration mock tests for Claim Initiation & Routing orchestration transformation.
 * Focuses on Final Policy Selection rules and analyst confirmation behaviors.
 */
@ExtendWith(MockitoExtension.class)
class DecisionFinalPolicySelectionRuleIfAnalystConfirmsTest {

    @Mock
    private PolicyClaimsDB policyClaimsDB;

    @Mock
    private ComplianceAuditService complianceAuditService;

    @Mock
    private DocumentStorageService documentStorageService;

    @InjectMocks
    private ClaimOrchestrationService claimOrchestrationService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> claimItemCaptor;

    @Captor
    private ArgumentCaptor<String> auditBucketCaptor;

    @Captor
    private ArgumentCaptor<String> auditKeyCaptor;

    @Test
    @DisplayName("Decision: Final policy selection | Rule: System auto-assigns flag for audit | Outcome: Single authoritative policy linked")
    void decisionFinalPolicySelectionRuleIfAnalystConfirmsLockPolicyIfSystemAutoAssignsFlagForAuditExpectedOutcomeSingleAuthoritativePolicyLinked() {
        // Given: System auto-assigns a policy (Analyst confirmation is null/false)
        String claimId = "CLM-AUTO-001";
        String autoAssignedPolicyId = "POL-SYS-999";
        ClaimInitiationRequest request = new ClaimInitiationRequest(
                claimId,
                autoAssignedPolicyId,
                null, // Analyst ID not present
                true  // systemAutoAssign flag
        );

        // Mock external dependencies
        when(policyClaimsDB.putItem(anyString(), anyString(), claimItemCaptor.capture()))
                .thenReturn(new ItemPayload("https://dynamodb/item/CLM-AUTO-001"));
        
        when(complianceAuditService.write(anyString(), anyString()))
                .thenReturn("s3://compliance/audit/CLM-AUTO-001.json");

        // When: Orchestration transformation executes
        ClaimTransformationResult result = claimOrchestrationService.transformAndRoute(request);

        // Then: Verify Single Authoritative Policy Linked
        assertNotNull(result);
        assertEquals(autoAssignedPolicyId, result.getLinkedPolicyId());
        assertEquals(1, result.getLinkedPolicies().size());
        assertTrue(result.getLinkedPolicies().get(0).isAuthoritative());
        assertFalse(result.getLinkedPolicies().get(0).isLockedByAnalyst()); // Not locked by analyst

        // Then: Verify System Auto-Assign triggers Audit Flag
        verify(complianceAuditService, times(1))
                .write(auditBucketCaptor.capture(), auditKeyCaptor.capture());
        
        String auditKey = auditKeyCaptor.getValue();
        assertTrue(auditKey.contains("audit"), "Audit key should indicate audit event");
        assertTrue(auditKey.contains(claimId), "Audit key should reference claim ID");

        // Then: Verify DB Item contains Audit Flag and Policy Link
        Map<String, Object> payload = claimItemCaptor.getValue();
        assertTrue((Boolean) payload.getOrDefault("auditFlag", false), "Item must have audit flag set");
        assertEquals(autoAssignedPolicyId, payload.get("linkedPolicyId"));
        assertNull(payload.get("analystConfirmedAt"), "Analyst confirmation timestamp should be null");
    }
}
