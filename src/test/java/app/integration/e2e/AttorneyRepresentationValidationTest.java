package app.integration.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.List;

// Real application services wired for E2E execution
import app.services.FnolIntakeService;
import app.services.ClaimStateService;
import app.services.TaskService;
import app.services.AuditLogService;
import app.services.SecureStorageService;
import app.models.ClaimState;
import app.models.Task;
import app.models.AuditEntry;
import app.models.CommunicationRouting;

public class AttorneyRepresentationValidationE2eTest {

    private FnolIntakeService fnolIntakeService;
    private ClaimStateService claimStateService;
    private TaskService taskService;
    private AuditLogService auditLogService;
    private SecureStorageService secureStorageService;

    @BeforeEach
    void setUp() {
        // Initialize real services (no mocks/stubs)
        fnolIntakeService = new FnolIntakeService();
        claimStateService = new ClaimStateService();
        taskService = new TaskService();
        auditLogService = new AuditLogService();
        secureStorageService = new SecureStorageService();
    }

    @Test
    void validate_fnol_intake_attorney_representation() {
        // Build inputs from test-case specification and constants sidecar
        Map<String, Object> intakePayload = Map.of(
            "policy_number", "POL-55555",
            "risk_address", "789 Elm Dr",
            "date_of_loss", "2024-04-20",
            "cause_of_loss", "Water Damage",
            "product_form", "DP3",
            "reporter_name", "Attorney John Law",
            "reporter_type", "Attorney",
            "attorney_represented", true,
            "letter_of_representation_id", "LET-789",
            "claim_channel", "API intake"
        );

        // 1. Submit FNOL intake and capture claim ID
        String claimId = fnolIntakeService.submitIntake(intakePayload);
        assertNotNull(claimId, "Claim ID must be generated upon successful intake submission");

        // 2. Verify state transition to Claim Opened with attorney represented flag
        ClaimState state = claimStateService.getState(claimId);
        assertEquals("Claim Opened", state.getStatus(), "Claim state must transition to Claim Opened");
        assertTrue(state.isAttorneyRepresented(), "Claim must have attorney represented flag enabled");

        // 3. Verify direct communication channel restriction and routing update
        CommunicationRouting routing = claimStateService.getCommunicationRouting(claimId);
        assertFalse(routing.allowsDirectContact(), "Direct communication channel must be restricted per legal rules");
        assertEquals("Attorney John Law", routing.getPrimaryContactName(), "Communication routing must update to attorney contact");

        // 4. Verify litigation review tasks generation
        List<Task> tasks = taskService.findByClaimId(claimId);
        Assertions.assertTrue(
            tasks.stream().anyMatch(t -> "Attorney Representation Review".equals(t.getType())),
            "Task 'Attorney Representation Review' must be created"
        );
        Assertions.assertTrue(
            tasks.stream().anyMatch(t -> "Coverage Counsel Review".equals(t.getType())),
            "Task 'Coverage Counsel Review' must be created"
        );

        // 5. Verify audit log records representation flag change
        List<AuditEntry> auditLogs = auditLogService.findByClaimId(claimId);
        Assertions.assertTrue(
            auditLogs.stream().anyMatch(a -> "representation_flag_changed".equals(a.getEventType())),
            "Audit log must record representation flag change"
        );

        // 6. Verify secure storage of representation document
        String documentUri = secureStorageService.getDocumentUri("LET-789");
        assertNotNull(documentUri, "Secure storage must capture the letter of representation");
        assertTrue(documentUri.startsWith("s3://"), "Document URI must resolve to secure storage location");
    }
}
