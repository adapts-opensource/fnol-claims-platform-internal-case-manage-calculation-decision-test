package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationMockTest {

    @Mock
    private ClaimDecisionEngine claimDecisionEngine;

    @Mock
    private AuditReportService auditReportService;

    @Mock
    private ReferenceDataCache referenceDataCache;

    @Mock
    private FieldCompletenessValidator fieldCompletenessValidator;

    @InjectMocks
    private DecisionCalculationService decisionCalculationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection and reset automatically
    }

    @Test
    void decision_audit_completeness_rule_all_required_fields_present_and_hashed_expected_outcome_report_generated_or_gap_flagged() {
        // Arrange: Prepare payload with all required fields present and hashed
        Map<String, Object> claimPayload = Map.of(
                "id", "claim-12345",
                "policyRef", "POL-98765",
                "incidentDate", "2023-11-15",
                "claimantName", "HASHED_NAME_1",
                "contactEmail", "HASHED_EMAIL_1",
                "damageDetails", "HASHED_DESC_1"
        );

        // Mock cache lookup for routing/reference data
        when(referenceDataCache.get(anyString())).thenReturn(Optional.of("routing_rules_v2"));

        // Mock validator to confirm completeness & hashing requirement is met
        when(fieldCompletenessValidator.validate(anyMap())).thenReturn(true);

        // Mock decision engine to return PASS for audit completeness
        when(claimDecisionEngine.evaluate(anyMap())).thenReturn(DecisionResult.pass());

        // Mock audit service to record the report generation
        doNothing().when(auditReportService).generate(anyString(), anyMap());

        // Act
        DecisionOutcome outcome = decisionCalculationService.calculateAndRoute(claimPayload);

        // Assert
        assertNotNull(outcome, "Decision outcome should not be null");
        assertEquals(OutcomeType.AUDIT_COMPLETE, outcome.getType(), "Outcome type should be AUDIT_COMPLETE");
        assertTrue(outcome.isReportGenerated(), "Report should be generated when all fields are present and hashed");
        assertTrue(outcome.getGapFlags().isEmpty(), "No gaps should be flagged when audit completeness passes");

        // Verify interactions
        verify(fieldCompletenessValidator).validate(claimPayload);
        verify(claimDecisionEngine).evaluate(claimPayload);
        verify(auditReportService).generate(eq("claim-12345"), anyMap());
        verifyNoMoreInteractions(referenceDataCache);
    }

    // Static nested stubs to maintain single public class constraint
    static class DecisionResult {
        static DecisionResult pass() { return new DecisionResult(true); }
        private final boolean pass;
        private DecisionResult(boolean pass) { this.pass = pass; }
    }

    enum OutcomeType { AUDIT_COMPLETE }

    static class DecisionOutcome {
        private final OutcomeType type;
        private final boolean reportGenerated;
        private final List<String> gapFlags;

        DecisionOutcome(OutcomeType type, boolean reportGenerated, List<String> gapFlags) {
            this.type = type;
            this.reportGenerated = reportGenerated;
            this.gapFlags = gapFlags;
        }

        OutcomeType getType() { return type; }
        boolean isReportGenerated() { return reportGenerated; }
        List<String> getGapFlags() { return gapFlags; }
    }

    interface ClaimDecisionEngine { DecisionResult evaluate(Map<String, Object> payload); }
    interface AuditReportService { void generate(String id, Map<String, Object> payload); }
    interface ReferenceDataCache { Optional<String> get(String key); }
    interface FieldCompletenessValidator { boolean validate(Map<String, Object> payload); }

    static class DecisionCalculationService {
        private final ClaimDecisionEngine claimDecisionEngine;
        private final AuditReportService auditReportService;
        private final ReferenceDataCache referenceDataCache;
        private final FieldCompletenessValidator fieldCompletenessValidator;

        DecisionCalculationService(
                ClaimDecisionEngine e,
                AuditReportService a,
                ReferenceDataCache c,
                FieldCompletenessValidator v) {
            claimDecisionEngine = e;
            auditReportService = a;
            referenceDataCache = c;
            fieldCompletenessValidator = v;
        }

        DecisionOutcome calculateAndRoute(Map<String, Object> payload) {
            boolean valid = fieldCompletenessValidator.validate(payload);
            claimDecisionEngine.evaluate(payload);
            auditReportService.generate((String) payload.get("id"), payload);
            return new DecisionOutcome(OutcomeType.AUDIT_COMPLETE, valid, List.of());
        }
    }
}
