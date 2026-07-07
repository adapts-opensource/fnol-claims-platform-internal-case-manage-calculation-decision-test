package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementDecisionInputValidationMockTest {

    @Mock
    private CodeSetValidator mockCodeSetValidator;
    @Mock
    private FreshnessValidator mockFreshnessValidator;

    private DecisionOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new DecisionOrchestrationService(mockCodeSetValidator, mockFreshnessValidator);
    }

    @Test
    void input_criteria_required_inputs_cause_of_loss_damage_severity_exposure_type_litigation_risk_indicator_regulatory_constraints_optional_inputs_estimated_loss_amount_vendor_availability_inspector_schedule_input_validation_cause_and_severity_must_be_in_approved_code_sets_litigation_risk_must_be_boolean_or_enum_freshness_requirements_triage_rules_must_be_current_within_24_hours_vendor_availability_must_be_refreshed_daily() {
        // Arrange: Missing required inputs
        DecisionInputCriteria missingRequired = new DecisionInputCriteria(
                null, "MODERATE", "PROPERTY", true, "REG_CONSTRAINTS",
                10000.0, "AVAILABLE", "2023-10-25"
        );
        assertFalse(orchestrationService.validateAndOrchestrate(missingRequired, System.currentTimeMillis()),
                "Orchestration should fail when required inputs are missing");

        // Arrange: Cause/Severity not in approved code sets
        DecisionInputCriteria invalidCodeSets = new DecisionInputCriteria(
                "UNKNOWN_CAUSE", "SEVERE", "PROPERTY", true, "REG_CONSTRAINTS",
                10000.0, "AVAILABLE", "2023-10-25"
        );
        when(mockCodeSetValidator.isInApprovedSet("UNKNOWN_CAUSE", "CAUSE_OF_LOSS")).thenReturn(false);
        when(mockCodeSetValidator.isInApprovedSet("SEVERE", "DAMAGE_SEVERITY")).thenReturn(false);
        assertFalse(orchestrationService.validateAndOrchestrate(invalidCodeSets, System.currentTimeMillis()),
                "Orchestration should fail when cause/severity are not in approved code sets");

        // Arrange: Litigation risk not boolean or enum
        DecisionInputCriteria invalidLitigationRisk = new DecisionInputCriteria(
                "FIRE", "MODERATE", "PROPERTY", "INVALID_TYPE", "REG_CONSTRAINTS",
                10000.0, "AVAILABLE", "2023-10-25"
        );
        when(mockCodeSetValidator.isInApprovedSet("FIRE", "CAUSE_OF_LOSS")).thenReturn(true);
        when(mockCodeSetValidator.isInApprovedSet("MODERATE", "DAMAGE_SEVERITY")).thenReturn(true);
        assertFalse(orchestrationService.validateAndOrchestrate(invalidLitigationRisk, System.currentTimeMillis()),
                "Orchestration should fail when litigation risk is not boolean or enum");

        // Arrange: Freshness requirements violated (> 24 hours)
        DecisionInputCriteria validInputs = new DecisionInputCriteria(
                "FIRE", "MODERATE", "PROPERTY", true, "REG_CONSTRAINTS",
                10000.0, "AVAILABLE", "2023-10-25"
        );
        long staleTimestamp = System.currentTimeMillis() - (25 * 60 * 60 * 1000L);
        when(mockCodeSetValidator.isInApprovedSet("FIRE", "CAUSE_OF_LOSS")).thenReturn(true);
        when(mockCodeSetValidator.isInApprovedSet("MODERATE", "DAMAGE_SEVERITY")).thenReturn(true);
        when(mockFreshnessValidator.isWithinHours(staleTimestamp, 24)).thenReturn(false);
        assertFalse(orchestrationService.validateAndOrchestrate(validInputs, staleTimestamp),
                "Orchestration should fail when triage rules or vendor availability exceed 24h freshness");

        // Arrange: All validations pass
        long freshTimestamp = System.currentTimeMillis() - (2 * 60 * 60 * 1000L);
        when(mockCodeSetValidator.isInApprovedSet("FIRE", "CAUSE_OF_LOSS")).thenReturn(true);
        when(mockCodeSetValidator.isInApprovedSet("MODERATE", "DAMAGE_SEVERITY")).thenReturn(true);
        when(mockFreshnessValidator.isWithinHours(freshTimestamp, 24)).thenReturn(true);
        assertTrue(orchestrationService.validateAndOrchestrate(validInputs, freshTimestamp),
                "Orchestration should succeed when all criteria, code sets, types, and freshness rules are valid");
    }

    // Test data record
    record DecisionInputCriteria(
            String causeOfLoss,
            String damageSeverity,
            String exposureType,
            Object litigationRiskIndicator,
            String regulatoryConstraints,
            Double estimatedLossAmount,
            String vendorAvailability,
            String inspectorSchedule
    ) {}

    // Mocked external dependency interfaces
    interface CodeSetValidator {
        boolean isInApprovedSet(String value, String category);
    }

    interface FreshnessValidator {
        boolean isWithinHours(long lastUpdatedEpoch, long maxHours);
    }

    // Service under test (orchestration logic)
    class DecisionOrchestrationService {
        private final CodeSetValidator codeSetValidator;
        private final FreshnessValidator freshnessValidator;

        DecisionOrchestrationService(CodeSetValidator codeSetValidator, FreshnessValidator freshnessValidator) {
            this.codeSetValidator = codeSetValidator;
            this.freshnessValidator = freshnessValidator;
        }

        boolean validateAndOrchestrate(DecisionInputCriteria input, long currentEpoch) {
            // 1. Required inputs validation
            if (input.causeOfLoss() == null || input.damageSeverity() == null ||
                input.exposureType() == null || input.litigationRiskIndicator() == null ||
                input.regulatoryConstraints() == null) {
                return false;
            }

            // 2. Code set validation
            if (!codeSetValidator.isInApprovedSet(input.causeOfLoss(), "CAUSE_OF_LOSS") ||
                !codeSetValidator.isInApprovedSet(input.damageSeverity(), "DAMAGE_SEVERITY")) {
                return false;
            }

            // 3. Type validation for litigation risk
            if (!(input.litigationRiskIndicator() instanceof Boolean) &&
                !(input.litigationRiskIndicator() instanceof Enum)) {
                return false;
            }

            // 4. Freshness validation (Triage rules & Vendor availability)
            if (!freshnessValidator.isWithinHours(currentEpoch, 24) ||
                !freshnessValidator.isWithinHours(currentEpoch, 24)) {
                return false;
            }

            return true;
        }
    }
}
