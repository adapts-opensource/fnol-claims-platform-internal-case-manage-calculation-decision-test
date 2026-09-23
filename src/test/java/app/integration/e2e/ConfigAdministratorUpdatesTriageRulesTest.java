package app.integration.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * E2E test for Claim Data Standardization:decision:validation
 * Verifies that Config Administrator updates to triage rules enforce version control and approval workflows.
 */
public class Us04ConfigAdministratorUpdatesTriageRulesTest {

    private ClaimDataStandardizationService standardizationService;
    private ClaimDataStandardizationDecisionValidation decisionValidationModel;

    @BeforeEach
    void setUp() {
        // Initialize real application services and models
        // Compile stubs under src/main/java/app/ are generated when no implementation package exists
        this.standardizationService = new ClaimDataStandardizationService();
        this.decisionValidationModel = new ClaimDataStandardizationDecisionValidation();
    }

    @Test
    void us_04_config_administrator_updates_triage_rules() {
        // Load constants/fixtures for sample data (Constants JSON sidecar)
        String ruleId = "rule_001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("routing_threshold", 0.85);
        payload.put("validation_criteria", java.util.List.of("severity", "claim_type", "deductible"));

        Map<String, Object> versionControl = new HashMap<>();
        versionControl.put("current_version", 2);
        versionControl.put("previous_version", 1);
        versionControl.put("change_log", "Updated routing threshold per business requirement");

        Map<String, Object> approval = new HashMap<>();
        approval.put("status", "PENDING_APPROVAL");
        approval.put("approver_role", "ConfigAdministrator");
        approval.put("approval_required", true);

        // Build inputs from test-case Inputs / Expected Results
        ClaimDataStandardizationDecisionValidation updateRequest = decisionValidationModel
                .createUpdateRequest(ruleId, payload, versionControl, approval);

        // Wire and invoke real services from app.services.* and app.models.*
        ClaimDataStandardizationDecisionValidation updatedResult = standardizationService
                .processDecisionValidation(updateRequest);

        // Expected Results: Rule changes enforce version control and approval
        assertNotNull(updatedResult, "Service must return updated decision validation record");
        assertEquals("rule_001", updatedResult.getId(), "ID must match the requested rule");
        assertEquals(2, updatedResult.getVersion(), "Version control must enforce version increment");
        assertTrue(updatedResult.isVersionHistoryTracked(), "Version control must maintain history");
        assertEquals("PENDING_APPROVAL", updatedResult.getApprovalStatus(), "Approval workflow must be triggered");
        assertTrue(updatedResult.isApprovalRequired(), "Approval must be enforced for configuration changes");
    }
}
