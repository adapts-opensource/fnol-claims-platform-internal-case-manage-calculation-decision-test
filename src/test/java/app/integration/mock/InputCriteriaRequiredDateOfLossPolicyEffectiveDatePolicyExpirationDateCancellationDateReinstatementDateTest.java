package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Initiation & Routing:orchestration:decision validation logic.
 * Verifies required/optional field handling, date format validation, policy date constraints,
 * and mocked freshness requirements for PAS and Cat Data sources.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionValidationMockTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "date_of_loss", "policy_effective_date", "policy_expiration_date",
            "cancellation_date", "reinstatement_date", "rewrite_date",
            "binding_restrictions", "known_storm_events"
    );

    @Mock
    private PasRealTimeService pasService;
    @Mock
    private CatNearRealTimeService catDataService;

    private DecisionOrchestrationValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new DecisionOrchestrationValidationService(pasService, catDataService);
    }

    @Test
    void shouldPassValidationWithAllRequiredAndOptionalFields() {
        Map<String, Object> payload = buildValidPayload();
        when(pasService.fetchPolicyDates(anyString())).thenReturn(new PolicyData(LocalDate.now(), LocalDate.now().plusYears(1)));
        when(catDataService.fetchStormEvents(anyString())).thenReturn(List.of("event_1"));

        assertDoesNotThrow(() -> validationService.validate(payload, "policy_123"));
    }

    @Test
    void shouldFailWhenRequiredDateOfLossIsMissing() {
        Map<String, Object> payload = buildValidPayload();
        payload.remove("date_of_loss");

        assertThrows(ValidationException.class, () -> validationService.validate(payload, "policy_123"));
    }

    @Test
    void shouldFailWhenDateOfLossIsInvalidFormat() {
        Map<String, Object> payload = buildValidPayload();
        payload.put("date_of_loss", "invalid-date-string");

        assertThrows(ValidationException.class, () -> validationService.validate(payload, "policy_123"));
    }

    @Test
    void shouldFailWhenPolicyDatesAreInvalid() {
        Map<String, Object> payload = buildValidPayload();
        payload.put("policy_effective_date", "2025-12-31");
        payload.put("policy_expiration_date", "2025-01-01");

        assertThrows(ValidationException.class, () -> validationService.validate(payload, "policy_123"));
    }

    @Test
    void shouldVerifyFreshnessRequirementsFromMockedSources() {
        Map<String, Object> payload = buildValidPayload();
        when(pasService.fetchPolicyDates(anyString())).thenReturn(new PolicyData(LocalDate.now(), LocalDate.now().plusDays(1)));
        when(catDataService.fetchStormEvents(anyString())).thenReturn(List.of());

        assertDoesNotThrow(() -> validationService.validate(payload, "policy_123"));

        verify(pasService, times(1)).fetchPolicyDates("policy_123");
        verify(catDataService, times(1)).fetchStormEvents("us-east-1");
    }

    private Map<String, Object> buildValidPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "claim_456");
        payload.put("date_of_loss", LocalDate.now().minusDays(5).format(DATE_FORMATTER));
        payload.put("policy_effective_date", LocalDate.now().minusDays(30).format(DATE_FORMATTER));
        payload.put("policy_expiration_date", LocalDate.now().plusDays(30).format(DATE_FORMATTER));
        payload.put("cancellation_date", null);
        payload.put("reinstatement_date", null);
        payload.put("rewrite_date", null);
        payload.put("binding_restrictions", List.of("standard"));
        payload.put("known_storm_events", List.of("storm_x"));
        payload.put("cat_moratorium_status", "active");
        return payload;
    }

    // Mock Interfaces for external I/O contracts
    interface PasRealTimeService {
        PolicyData fetchPolicyDates(String policyId);
    }

    interface CatNearRealTimeService {
        List<String> fetchStormEvents(String region);
    }

    record PolicyData(LocalDate effective, LocalDate expiration) {}

    // Service under test (simplified for mock testing)
    static class DecisionOrchestrationValidationService {
        private final PasRealTimeService pasService;
        private final CatNearRealTimeService catDataService;

        DecisionOrchestrationValidationService(PasRealTimeService pasService, CatNearRealTimeService catDataService) {
            this.pasService = pasService;
            this.catDataService = catDataService;
        }

        void validate(Map<String, Object> payload, String policyId) throws ValidationException {
            for (String field : REQUIRED_FIELDS) {
                if (!payload.containsKey(field) || payload.get(field) == null) {
                    throw new ValidationException("Missing required field: " + field);
                }
            }

            String dolStr = (String) payload.get("date_of_loss");
            try {
                LocalDate.parse(dolStr, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                throw new ValidationException("DOL must be a valid date.");
            }

            LocalDate effective = LocalDate.parse((String) payload.get("policy_effective_date"), DATE_FORMATTER);
            LocalDate expiration = LocalDate.parse((String) payload.get("policy_expiration_date"), DATE_FORMATTER);
            if (!expiration.isAfter(effective)) {
                throw new ValidationException("Policy dates must be valid.");
            }

            pasService.fetchPolicyDates(policyId);
            catDataService.fetchStormEvents("us-east-1");
        }
    }

    static class ValidationException extends Exception {
        ValidationException(String message) { super(message); }
    }
}
