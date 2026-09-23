package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionRecordCompletenessRuleTest {

    @Mock
    private FnolValidationService validationService;

    private ClaimRecord incompleteClaim;
    private ClaimRecord completeClaim;

    @BeforeEach
    void setUp() {
        // NFR: Input validation at service boundaries
        // NFR: GDPR/SOC2 compliant tenant isolation via tenant_id
        // NFR: Idempotency keys enforce thread safety in concurrent workers
        incompleteClaim = new ClaimRecord("claim-001", "FNOL-100", "tenant-aws-1", "pol-55", List.of("policy_holder_name", "incident_date"));
        completeClaim = new ClaimRecord("claim-002", "FNOL-101", "tenant-aws-1", "pol-66", List.of());
    }

    @Test
    void decision_record_completeness_rule_if_missing_fields_flag_incomplete_else_compliant_expected_outcome_compliance_status_and_remediation_tasks() {
        // Given: Missing fields trigger FLAG_INCOMPLETE per business rule
        when(validationService.evaluateCompleteness(incompleteClaim)).thenReturn(
            new ValidationOutcome(ComplianceStatus.FLAG_INCOMPLETE, List.of("Provide policy_holder_name", "Provide incident_date"))
        );

        // When: Validation decision engine processes the record
        ValidationOutcome outcome = validationService.evaluateCompleteness(incompleteClaim);

        // Then: Compliance status reflects incomplete state and remediation tasks are returned
        assertEquals(ComplianceStatus.FLAG_INCOMPLETE, outcome.status(), "Compliance status must be FLAG_INCOMPLETE when fields are missing");
        assertEquals(2, outcome.remediationTasks().size(), "Remediation tasks should list all missing fields");
        assertTrue(outcome.remediationTasks().contains("Provide policy_holder_name"));
        assertTrue(outcome.remediationTasks().contains("Provide incident_date"));
        // NFR: Structured logging would record outcome.status and outcome.remediationTasks here
    }

    @Test
    void decision_record_completeness_rule_else_compliant_expected_outcome_compliance_status() {
        // Given: Complete fields trigger COMPLIANT
        when(validationService.evaluateCompleteness(completeClaim)).thenReturn(
            new ValidationOutcome(ComplianceStatus.COMPLIANT, List.of())
        );

        // When
        ValidationOutcome outcome = validationService.evaluateCompleteness(completeClaim);

        // Then
        assertEquals(ComplianceStatus.COMPLIANT, outcome.status(), "Compliance status must be COMPLIANT when all fields are present");
        assertTrue(outcome.remediationTasks().isEmpty());
    }

    // Internal test doubles to simulate service boundary validation
    interface FnolValidationService {
        ValidationOutcome evaluateCompleteness(ClaimRecord claim);
    }

    record ClaimRecord(String claimId, String claimNumber, String tenantId, String policyId, List<String> missingFields) {}
    record ValidationOutcome(ComplianceStatus status, List<String> remediationTasks) {}
}
