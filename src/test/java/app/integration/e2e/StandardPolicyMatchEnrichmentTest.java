package app.integration.e2e;

import app.models.ClaimSubmissionRequest;
import app.models.ClaimSubmissionResponse;
import app.models.PolicySnapshot;
import app.models.Task;
import app.services.ClaimSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StandardPolicyMatchEnrichment E2E Tests")
class StandardPolicyMatchEnrichmentE2eTest {

    private ClaimSubmissionService claimSubmissionService;

    // Test-case Inputs mapped to constants
    private static final String POLICY_NUMBER = "POL-FL-1001";
    private static final String DATE_OF_LOSS = "2024-05-15T10:00:00Z";
    private static final String CAUSE_OF_LOSS = "wind";
    private static final String REPORTER_TYPE = "insured";
    private static final String RISK_ADDRESS = "123 Palm Ave";
    private static final String PRODUCT_FORM = "HO3";
    private static final String CONTACT_EMAIL = "test@example.com";
    private static final String LOSS_DESCRIPTION = "Wind damage to roof shingles";

    // Expected Results constants
    private static final String EXPECTED_CLAIM_STATUS = "Claim Opened";
    private static final Pattern CLAIM_NUMBER_PATTERN = Pattern.compile("CLM-FL01-2024-\\d{4}");
    private static final String EXPECTED_TASK_1 = "Review FNOL";
    private static final String EXPECTED_TASK_2 = "Acknowledge Claim";
    private static final String EXPECTED_QUEUE_STATUS = "Queued";

    @BeforeEach
    void setUp() {
        // In E2E mode, services are wired via application context or DI container.
        // Stubs under src/main/java/app/ provide compile-time contracts for real service interfaces.
        claimSubmissionService = new ClaimSubmissionService();
    }

    @Test
    @DisplayName("submit_fnol_standard_policy_match_enrichment")
    void submit_fnol_standard_policy_match_enrichment() {
        // 1. Build Input Payload from test-case Inputs
        ClaimSubmissionRequest request = new ClaimSubmissionRequest();
        request.setPolicyNumber(POLICY_NUMBER);
        request.setDateOfLoss(LocalDateTime.parse(DATE_OF_LOSS, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        request.setCauseOfLoss(CAUSE_OF_LOSS);
        request.setReporterType(REPORTER_TYPE);
        request.setRiskAddress(RISK_ADDRESS);
        request.setProductForm(PRODUCT_FORM);
        request.setContactEmail(CONTACT_EMAIL);
        request.setLossDescription(LOSS_DESCRIPTION);

        // 2. Invoke Real Application Service (E2E flow: policy match -> enrichment -> task routing)
        ClaimSubmissionResponse response = claimSubmissionService.submitFnol(request);

        // 3. Assert Expected Results: claim_status=Claim Opened
        assertEquals(EXPECTED_CLAIM_STATUS, response.getClaimStatus(), "Claim status should be opened");

        // 4. Assert Expected Results: claim_number matches CLM-FL01-2024-XXXX format
        assertNotNull(response.getClaimNumber(), "Claim number must be generated");
        assertTrue(CLAIM_NUMBER_PATTERN.matcher(response.getClaimNumber()).matches(),
                "Claim number must match CLM-FL01-2024-XXXX format");

        // 5. Assert Expected Results: policy_snapshot.retrieved=true & product_form=HO3
        PolicySnapshot snapshot = response.getPolicySnapshot();
        assertNotNull(snapshot, "Policy snapshot should be retrieved during enrichment");
        assertTrue(snapshot.isRetrieved(), "Policy snapshot.retrieved should be true");
        assertEquals(PRODUCT_FORM, snapshot.getProductForm(), "Policy snapshot.product_form should match HO3");

        // 6. Assert Expected Results: tasks contains Review FNOL and Acknowledge Claim
        List<Task> tasks = response.getTasks();
        assertNotNull(tasks, "Intake tasks list should not be null");
        assertTrue(tasks.stream().anyMatch(t -> EXPECTED_TASK_1.equals(t.getName())),
                "Tasks must contain '" + EXPECTED_TASK_1 + "'");
        assertTrue(tasks.stream().anyMatch(t -> EXPECTED_TASK_2.equals(t.getName())),
                "Tasks must contain '" + EXPECTED_TASK_2 + "'");

        // 7. Assert Expected Results: acknowledgment_queue.status=Queued
        assertEquals(EXPECTED_QUEUE_STATUS, response.getAcknowledgmentQueueStatus(),
                "Acknowledgment queue status should be Queued");

        // 8. Assert Expected Results: audit_log captures policy match action and claim number generation
        Map<String, String> auditLog = response.getAuditLog();
        assertNotNull(auditLog, "Audit log should capture enrichment actions");
        boolean hasPolicyMatch = auditLog.containsKey("policy_match_action") || auditLog.values().stream()
                .anyMatch(v -> v.toLowerCase().contains("policy match"));
        boolean hasClaimGen = auditLog.containsKey("claim_number_generation") || auditLog.values().stream()
                .anyMatch(v -> v.toLowerCase().contains("claim number"));
        assertTrue(hasPolicyMatch && hasClaimGen, "Audit log must capture policy match action and claim number generation");
    }
}
