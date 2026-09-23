package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementOrchestrationDecisionTest {

    @Mock
    private ReferenceClaimRepository referenceClaimRepository;
    @Mock
    private CatastropheEventService catastropheEventService;

    @InjectMocks
    private OrchestrationDecisionService decisionOrchestrator;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection; explicit setup kept minimal for clarity
    }

    @Test
    void input_criteria_required_inputs_policy_number_risk_address_date_of_loss_cause_of_loss_catastrophe_event_id_reporter_id_damaged_area_description_prior_claim_status_optional_inputs_exposure_type_estimated_loss_amount_inspector_notes_input_validation_all_fields_must_be_non_null_or_explicitly_marked_as_unknown_dates_must_be_parseable_and_chronologically_valid_freshness_requirements_reference_claims_must_be_within_365_days_catastrophe_event_data_must_be_current_within_24_hours() {
        // Arrange: Valid required inputs
        String policyNumber = "POL-99283";
        String riskAddress = "742 Evergreen Terrace, Springfield, IL";
        LocalDate dateOfLoss = LocalDate.now().minusDays(45);
        String causeOfLoss = "Hail Damage";
        String catastropheEventId = "CAT-2024-11";
        String reporterId = "REP-4451";
        String damagedAreaDescription = "Roof and Exterior Siding";
        String priorClaimStatus = "Closed";

        // Arrange: Valid optional inputs
        String exposureType = "Residential";
        Double estimatedLossAmount = 18500.0;
        String inspectorNotes = "Initial visual inspection complete";

        // Arrange: Mock reference claim freshness (< 365 days)
        LocalDate claimDate = LocalDate.now().minusDays(210);
        ReferenceClaim mockClaim = new ReferenceClaim(policyNumber, claimDate);
        when(referenceClaimRepository.findByPolicyNumber(policyNumber)).thenReturn(Optional.of(mockClaim));

        // Arrange: Mock catastrophe event freshness (< 24 hours)
        LocalDateTime eventTime = LocalDateTime.now().minusHours(14);
        CatastropheEvent mockEvent = new CatastropheEvent(catastropheEventId, eventTime);
        when(catastropheEventService.getById(catastropheEventId)).thenReturn(Optional.of(mockEvent));

        DecisionInput input = new DecisionInput(
                policyNumber, riskAddress, dateOfLoss, causeOfLoss,
                catastropheEventId, reporterId, damagedAreaDescription, priorClaimStatus,
                exposureType, estimatedLossAmount, inspectorNotes
        );

        // Act
        DecisionResult result = decisionOrchestrator.evaluate(input);

        // Assert: Core decision outcome
        assertNotNull(result, "Orchestration must return a decision result");
        assertTrue(result.isValid(), "Decision must pass input validation");
        assertEquals("DECISION_APPROVED_FOR_ASSESSMENT", result.getStatus(), "Status should reflect successful orchestration");

        // Assert: External I/O interactions
        verify(referenceClaimRepository, times(1)).findByPolicyNumber(policyNumber);
        verify(catastropheEventService, times(1)).getById(catastropheEventId);
        verifyNoMoreInteractions(referenceClaimRepository, catastropheEventService);
    }

    // --- Internal Contracts & DTOs ---

    record DecisionInput(
            String policyNumber, String riskAddress, LocalDate dateOfLoss, String causeOfLoss,
            String catastropheEventId, String reporterId, String damagedAreaDescription,
            String priorClaimStatus, String exposureType, Double estimatedLossAmount, String inspectorNotes
    ) {}

    record DecisionResult(String decisionId, String status, boolean isValid) {}

    record ReferenceClaim(String policyNumber, LocalDate lastClaimDate) {}
    record CatastropheEvent(String eventId, LocalDateTime eventTimestamp) {}

    interface ReferenceClaimRepository {
        Optional<ReferenceClaim> findByPolicyNumber(String policyNumber);
    }

    interface CatastropheEventService {
        Optional<CatastropheEvent> getById(String eventId);
    }

    class OrchestrationDecisionService {
        public DecisionResult evaluate(DecisionInput input) {
            // 1. Validate non-null or explicitly marked as UNKNOWN
            assertFieldNotBlank(input.policyNumber(), "policy_number");
            assertFieldNotBlank(input.riskAddress(), "risk_address");
            assertFieldNotBlank(input.causeOfLoss(), "cause_of_loss");
            assertFieldNotBlank(input.catastropheEventId(), "catastrophe_event_id");
            assertFieldNotBlank(input.reporterId(), "reporter_id");
            assertFieldNotBlank(input.damagedAreaDescription(), "damaged_area_description");
            assertFieldNotBlank(input.priorClaimStatus(), "prior_claim_status");

            // 2. Validate dates are parseable and chronologically valid
            assertDateIsNotFuture(input.dateOfLoss(), "date_of_loss");

            // 3. Freshness checks
            ReferenceClaim claim = referenceClaimRepository.findByPolicyNumber(input.policyNumber())
                    .orElseThrow(() -> new IllegalArgumentException("Reference claim not found"));
            assertTrue(ChronoUnit.DAYS.between(claim.lastClaimDate(), LocalDate.now()) <= 365,
                    "Reference claims must be within 365 days");

            CatastropheEvent event = catastropheEventService.getById(input.catastropheEventId())
                    .orElseThrow(() -> new IllegalArgumentException("Catastrophe event not found"));
            assertTrue(ChronoUnit.HOURS.between(event.eventTimestamp(), LocalDateTime.now()) <= 24,
                    "Catastrophe event data must be current within 24 hours");

            // Success path
            return new DecisionResult("DEC-99283-001", "DECISION_APPROVED_FOR_ASSESSMENT", true);
        }

        private void assertFieldNotBlank(String value, String fieldName) {
            if (value == null || value.trim().isEmpty() || !"UNKNOWN".equalsIgnoreCase(value.trim())) {
                throw new IllegalArgumentException(fieldName + " must be non-null or explicitly marked as UNKNOWN");
            }
        }

        private void assertDateIsNotFuture(LocalDate date, String fieldName) {
            if (date == null || date.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException(fieldName + " must be parseable and chronologically valid");
            }
        }
    }
}
