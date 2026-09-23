package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Data Standardization:decision:enrichment.
 * Verifies behavior of external service interactions and decision logic
 * without invoking live AWS or production APIs.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionEnrichmentMockTest {

    @Mock
    private RulesEngineDecisionService rulesEngineDecisionService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @InjectMocks
    private ClaimDataStandardizationDecisionEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        // Reset mocks between tests if using shared state patterns
        // MockitoExtension handles this, but explicit reset can be added for complex scenarios
    }

    /**
     * Test Case: RulesRequireApprovalBeforeActivation
     * Description: Rules require approval before activation
     * 
     * Validates that when decision rules indicate a high-risk or restricted scenario,
     * the enrichment process:
     * 1. Invokes the Rules Engine.
     * 2. Updates the decision status to PENDING_APPROVAL.
     * 3. Routes a task to the Workflow Router for manual approval.
     * 4. Writes an audit log to S3.
     * 5. Ensures the decision is NOT activated.
     */
    @Test
    @DisplayName("Rules require approval before activation")
    void rules_require_approval_before_activation() {
        // Given: Setup claim payload triggering approval rules
        String claimId = "CLM-STD-ENRICH-001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("payload", Map.of(
            "claimType", "AUTO",
            "damageAmount", 45000.0,
            "fraudScore", 0.92,
            "jurisdiction", "NY"
        ));

        // Mock Rules Engine response indicating approval is required
        DecisionResult ruleResult = new DecisionResult();
        ruleResult.setStatus(DecisionStatus.PENDING_APPROVAL);
        ruleResult.setRequiresApproval(true);
        ruleResult.setActivationBlocked(true);
        ruleResult.setReason("High fraud score exceeds dynamic threshold");

        when(rulesEngineDecisionService.evaluate(anyMap())).thenReturn(ruleResult);

        // When: Execute enrichment logic
        enrichmentService.processEnrichment(payload);

        // Then: Verify interactions and state
        // 1. Rules Engine was called with the payload
        verify(rulesEngineDecisionService, times(1)).evaluate(payload);

        // 2. Decision status reflects pending approval
        assertTrue(ruleResult.isRequiresApproval(), "Decision should require approval");
        assertEquals(DecisionStatus.PENDING_APPROVAL, ruleResult.getStatus(), "Status should be PENDING_APPROVAL");
        assertFalse(ruleResult.isActivated(), "Decision must not be activated");

        // 3. Workflow Task Router created an approval task
        verify(workflowTaskRouter, times(1)).createTask(
            eq(claimId),
            eq(TaskType.MANUAL_APPROVAL),
            eq(ruleResult.getReason())
        );

        // 4. Audit Diary Store logged the decision event
        verify(auditDiaryStore, times(1)).writeAuditLog(
            eq("ClaimDataStandardization"),
            eq("decision_enrichment"),
            eq(claimId),
            any(AuditEvent.class)
        );

        // 5. Input validation NFR check (simulated via payload structure)
        assertNotNull(payload.get("id"), "Payload must contain claim ID");
        assertTrue(((Map<?, ?>) payload.get("payload")).containsKey("fraudScore"), "Payload must contain risk metrics");
    }
}
