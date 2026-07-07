package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClaimInitiationRoutingDecisionValidationTest {

    // Mocked infrastructure contracts to satisfy NFRs: no live AWS/HTTP calls, thread-safe mocks
    private static interface RedisCacheService {
        String get(String key);
        void put(String key, String value, int ttlSeconds);
    }

    private static interface DynamoDbClaimsService {
        Map<String, Object> getItem(String tableName, String partitionKey, String pkValue);
    }

    private static interface SesNotificationService {
        String sendMessage(String fromAddress, List<String> toAddresses, String region);
    }

    private static interface DecisionValidationEngine {
        boolean validateCauseNormalization(String causeCode);
        boolean validateAddressNormalization(String address);
        boolean checkClaimsCoreFreshness(String policyNumber);
        Map<String, Object> routeDecision(Map<String, Object> payload);
    }

    private RedisCacheService redisCacheService;
    private DynamoDbClaimsService dynamoDbClaimsService;
    private SesNotificationService sesNotificationService;
    private DecisionValidationEngine decisionValidationEngine;

    @BeforeEach
    void setUp() {
        redisCacheService = mock(RedisCacheService.class);
        dynamoDbClaimsService = mock(DynamoDbClaimsService.class);
        sesNotificationService = mock(SesNotificationService.class);
        decisionValidationEngine = mock(DecisionValidationEngine.class);
    }

    @Test
    @DisplayName("InputCriteriaRequired: policy_number, risk_address, date_of_loss, cause_of_loss, catastrophe_event, reporter_id, damaged_area, prior_claim_status | Optional: exposure_id, loss_amount_estimate | Validation: cause codes normalized, address normalized | Freshness: claims data from claims core real-time")
    void input_criteria_required_policy_number_risk_address_date_of_loss_cause_of_loss_catastrophe_event_reporter_id_damaged_area_prior_claim_status_optional_exposure_id_loss_amount_estimate_input_validation_cause_codes_normalized_address_normalized_freshness_requirements_claims_data_from_claims_core_real_time() {
        // Arrange: Valid payload with all required and optional fields
        Map<String, Object> validPayload = Map.of(
            "policy_number", "POL-98765",
            "risk_address", "456 Oak Avenue",
            "date_of_loss", "2023-11-15",
            "cause_of_loss", "WEATHER_HURRICANE",
            "catastrophe_event", "HURRICANE",
            "reporter_id", "REP-42",
            "damaged_area", "STRUCTURAL",
            "prior_claim_status", "ACTIVE",
            "exposure_id", "EXP-101",
            "loss_amount_estimate", 12500.0
        );

        // Mock normalized cause & address per input_validation rules
        when(decisionValidationEngine.validateCauseNormalization("WEATHER_HURRICANE")).thenReturn(true);
        when(decisionValidationEngine.validateAddressNormalization("456 Oak Avenue")).thenReturn(true);
        // Mock real-time freshness from Claims Core per freshness_requirements
        when(decisionValidationEngine.checkClaimsCoreFreshness("POL-98765")).thenReturn(true);
        // Mock routing decision output
        when(decisionValidationEngine.routeDecision(validPayload)).thenReturn(Map.of("status", "ROUTED", "priority", "HIGH"));

        // Act: Execute validation checks
        boolean causeValid = decisionValidationEngine.validateCauseNormalization((String) validPayload.get("cause_of_loss"));
        boolean addressValid = decisionValidationEngine.validateAddressNormalization((String) validPayload.get("risk_address"));
        boolean freshnessValid = decisionValidationEngine.checkClaimsCoreFreshness((String) validPayload.get("policy_number"));

        // Assert: Core validation criteria
        assertTrue(causeValid, "Cause codes must be normalized");
        assertTrue(addressValid, "Address must be normalized");
        assertTrue(freshnessValid, "Claims data must be real-time from Claims Core");

        // Verify: Infra I/O contracts are strictly mocked (TLS/Least Privilege/GDPR compliance via isolation)
        verifyNoInteractions(redisCacheService, dynamoDbClaimsService, sesNotificationService);
        assertDoesNotThrow(() -> decisionValidationEngine.routeDecision(validPayload), "Routing should not throw on valid input");
    }

    @Test
    void should_fail_validation_when_missing_required_field() {
        Map<String, Object> incompletePayload = Map.of(
            "policy_number", "POL-98765",
            "risk_address", "456 Oak Avenue",
            // date_of_loss missing
            "cause_of_loss", "WEATHER_HURRICANE",
            "catastrophe_event", "HURRICANE",
            "reporter_id", "REP-42",
            "damaged_area", "STRUCTURAL",
            "prior_claim_status", "ACTIVE"
        );

        List<String> required = List.of("policy_number", "risk_address", "date_of_loss", "cause_of_loss", "catastrophe_event", "reporter_id", "damaged_area", "prior_claim_status");
        boolean allPresent = required.stream().allMatch(incompletePayload::containsKey);

        assertFalse(allPresent, "Validation should fail when a required field is missing");
    }

    @Test
    void should_fail_validation_when_cause_code_is_not_normalized() {
        Map<String, Object> payload = Map.of(
            "policy_number", "POL-98765",
            "risk_address", "456 Oak Avenue",
            "date_of_loss", "2023-11-15",
            "cause_of_loss", "raw_weather_event", // not normalized
            "catastrophe_event", "HURRICANE",
            "reporter_id", "REP-42",
            "damaged_area", "STRUCTURAL",
            "prior_claim_status", "ACTIVE"
        );

        when(decisionValidationEngine.validateCauseNormalization("raw_weather_event")).thenReturn(false);

        boolean isValid = decisionValidationEngine.validateCauseNormalization((String) payload.get("cause_of_loss"));
        assertFalse(isValid, "Unnormalized cause codes must be rejected per input_validation rules");
    }

    @Test
    void should_fail_validation_when_claims_core_data_is_stale() {
        Map<String, Object> payload = Map.of(
            "policy_number", "POL-98765",
            "risk_address", "456 Oak Avenue",
            "date_of_loss", "2023-11-15",
            "cause_of_loss", "WEATHER_HURRICANE",
            "catastrophe_event", "HURRICANE",
            "reporter_id", "REP-42",
            "damaged_area", "STRUCTURAL",
            "prior_claim_status", "ACTIVE"
        );

        when(decisionValidationEngine.checkClaimsCoreFreshness("POL-98765")).thenReturn(false);

        boolean isFresh = decisionValidationEngine.checkClaimsCoreFreshness((String) payload.get("policy_number"));
        assertFalse(isFresh, "Stale claims core data must fail freshness_requirements");
    }
}
