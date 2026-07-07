package app.integration.e2e;

import app.models.ClaimDataStandardizationDecisionValidation;
import app.services.ClaimDecisionValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class ComplianceAuditorReviewsFnolDecisionsTest {

    private ClaimDecisionValidationService decisionValidationService;
    private Map<String, Object> testConstants;

    @BeforeEach
    void setUp() {
        // Instantiate real application services (or generated compile stubs)
        this.decisionValidationService = new ClaimDecisionValidationService();
        this.testConstants = loadConstantsFromSidecar();
    }

    @Test
    void us_05_compliance_auditor_reviews_fnol_decisions() {
        // Inputs: Regulatory audit or internal compliance review initiated
        String auditTrigger = (String) testConstants.get("audit_trigger");
        Map<String, Object> fnolDecisionPayload = Map.of(
                "fnol_reference", testConstants.get("fnol_reference"),
                "initial_decision", testConstants.get("initial_decision"),
                "audit_initiated", true,
                "reviewer_role", testConstants.get("reviewer_role"),
                "audit_trigger", auditTrigger
        );

        ClaimDataStandardizationDecisionValidation claimInput = new ClaimDataStandardizationDecisionValidation();
        claimInput.setId((String) testConstants.get("claim_id"));
        claimInput.setPayload(fnolDecisionPayload);

        // Exercise REAL application services end-to-end
        ClaimDataStandardizationDecisionValidation validatedClaim =
                decisionValidationService.processDecisionForComplianceReview(claimInput);

        // Expected Results & Assertions
        assertNotNull(validatedClaim, "Service must return a validated claim data entity");
        assertEquals(testConstants.get("claim_id"), validatedClaim.getId(),
                "Claim ID must match input");
        assertTrue((boolean) validatedClaim.getPayload().get("audit_initiated"),
                "Audit initiation flag must be preserved");
        assertEquals(testConstants.get("decision_status"), validatedClaim.getPayload().get("decision_status"),
                "Decision status must transition to compliance reviewed state");
        assertNotNull(validatedClaim.getPayload().get("audit_timestamp"),
                "Audit timestamp must be recorded upon review");
    }

    private Map<String, Object> loadConstantsFromSidecar() {
        // Loads sample data fixtures from the Constants JSON sidecar
        return Map.of(
                "fnol_reference", "FNOL-2024-8821",
                "claim_id", "CLM-2024-8821",
                "initial_decision", "PENDING",
                "audit_initiated", true,
                "reviewer_role", "ComplianceAuditor",
                "audit_trigger", "Regulatory audit or internal compliance review initiated",
                "decision_status", "COMPLIANCE_REVIEWED",
                "audit_timestamp", "2024-05-20T14:30:00Z"
        );
    }
}
