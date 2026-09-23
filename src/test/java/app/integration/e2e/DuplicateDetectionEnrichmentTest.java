package app.integration.e2e;
import app.models.FnolSubmissionRequest;
import app.models.FnolSubmissionResponse;
import app.services.FnolSubmissionService;
import app.services.ClaimEnrichmentService;
import app.services.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DuplicateDetectionEnrichmentE2eTest {

    private FnolSubmissionService fnolSubmissionService;
    private ClaimEnrichmentService enrichmentService;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        // Initialize real application services for end-to-end execution
        // Compile stubs under src/main/java/app/ are generated when no implementation package exists.
        fnolSubmissionService = new FnolSubmissionService();
        enrichmentService = new ClaimEnrichmentService();
        auditLogService = new AuditLogService();
    }

    @Test
    void submit_fnol_duplicate_detection_enrichment() {
        // Build inputs from test-case Inputs / Expected Results and the Constants JSON sidecar
        FnolSubmissionRequest request = new FnolSubmissionRequest();
        request.setPolicyNumber("POL-DUP-001");
        request.setDateOfLoss(LocalDate.of(2024, 3, 20));
        request.setCauseOfLoss("water");
        request.setRiskAddress("321 River Ln");
        request.setReporter("SameReporter");
        request.setLossDescription("Water leak damage");

        // Execute real E2E flow: submission -> enrichment -> audit
        FnolSubmissionResponse response = fnolSubmissionService.processSubmission(request);

        // Verify Expected Results
        assertTrue(response.isDuplicateMatch(), "duplicate_match should be true");
        assertEquals("Duplicate Review", response.getClaimStatus(), "claim_status should be Duplicate Review");

        List<String> tasks = response.getTasks();
        assertTrue(tasks.stream().anyMatch(t -> t.contains("Review Potential Duplicate Claim")),
                "tasks should contain Review Potential Duplicate Claim");

        Map<String, Object> triage = response.getTriageDimensions();
        assertTrue(Boolean.TRUE.equals(triage.get("duplicate_indicator")),
                "triage_dimensions.duplicate_indicator should be true");

        assertTrue(Boolean.TRUE.equals(response.getIsoClaimSearchCheckExecuted()),
                "iso_claimsearch.check_executed should be true");

        List<Map<String, String>> auditLogs = auditLogService.retrieveRecentEntries();
        boolean hasDuplicateAction = auditLogs.stream().anyMatch(log ->
                "duplicate detection action".equals(log.get("action")));
        boolean hasIsoCall = auditLogs.stream().anyMatch(log ->
                "ISO ClaimSearch integration call".equals(log.get("action")));
        assertTrue(hasDuplicateAction && hasIsoCall,
                "audit_log should capture duplicate detection action and ISO ClaimSearch integration call");
    }
}
