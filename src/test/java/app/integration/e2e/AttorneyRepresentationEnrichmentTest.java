package app.integration.e2e;

import app.models.FnolSubmissionRequest;
import app.models.ClaimOutcome;
import app.services.FnolSubmissionService;
import app.services.TaskService;
import app.services.CommunicationRuleService;
import app.services.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

public class AttorneyRepresentationEnrichmentTest {

    private FnolSubmissionService fnolSubmissionService;
    private TaskService taskService;
    private CommunicationRuleService communicationRuleService;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        fnolSubmissionService = new FnolSubmissionService();
        taskService = new TaskService();
        communicationRuleService = new CommunicationRuleService();
        auditLogService = new AuditLogService();
    }

    @Test
    void submit_fnol_attorney_representation_enrichment() {
        // Build inputs from test-case Inputs and Constants JSON sidecar
        FnolSubmissionRequest request = new FnolSubmissionRequest();
        request.setAttorneyRepresented(true);
        request.setAttorneyName("Jane Smith");
        request.setAttorneyBarNumber("12345");
        request.setProductForm("HO3");
        request.setCauseOfLoss("fire");
        request.setRiskAddress("789 Oak St");
        request.setReporterType("insured");
        request.setLossDescription("Interior fire damage");

        // Wire and invoke real application services end-to-end
        ClaimOutcome outcome = fnolSubmissionService.submit(request);

        // Assert Expected Results
        assertEquals("Represented claim", outcome.getClaimType(), "Claim type must be marked as represented");
        assertTrue(outcome.getTasks().stream().anyMatch(t -> t.getName().equals("Attorney Representation Review")),
                "Tasks must contain Attorney Representation Review");
        assertTrue(outcome.getTasks().stream().anyMatch(t -> t.getName().equals("Review FNOL")),
                "Tasks must contain Review FNOL");
        assertTrue(outcome.getCommunicationRules().isDirectCommunicationRestricted(),
                "Direct communications must be restricted when attorney represented");
        assertNotNull(outcome.getAuditLog(), "Audit log must capture submission details");
        assertTrue(outcome.getAuditLog().contains("attorney_flag"), "Audit log must capture attorney flag");
        assertTrue(outcome.getAuditLog().contains("representation_task_creation"),
                "Audit log must capture representation task creation");
        assertEquals("Claim Opened", outcome.getClaimStatus(), "Claim status should be opened");
    }
}
