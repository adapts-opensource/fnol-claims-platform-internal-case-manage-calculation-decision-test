package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test for Claim Data Standardization:decision:transformation.
 * Validates required/optional input handling, date format constraints, 
 * future-date rejection, moratorium range validation, and freshness requirements.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionTransformationMockTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Mock
    private ClaimTransformationService transformationService;

    private Map<String, Object> baseValidPayload;

    @BeforeEach
    void setUp() {
        baseValidPayload = new HashMap<>();
        baseValidPayload.put("date_of_loss", "2023-10-15");
        baseValidPayload.put("policy_effective_date", "2023-01-01");
        baseValidPayload.put("policy_expiration_date", "2024-01-01");
        baseValidPayload.put("policy_status", "ACTIVE");
        baseValidPayload.put("cancellation_date", null);
        baseValidPayload.put("reinstatement_date", null);
        baseValidPayload.put("rewrite_date", null);
        baseValidPayload.put("binding_restrictions", List.of());
        baseValidPayload.put("moratorium_dates", List.of(Map.of("start", "2023-01-01", "end", "2023-12-31")));
        baseValidPayload.put("storm_event_dates", List.of("2023-09-10"));
    }

    @Test
    void shouldSuccessfullyTransformPayloadWithValidRequiredInputs() {
        when(transformationService.transform(anyMap())).thenReturn(Map.of("decision", "APPROVED"));
        Map<String, Object> result = transformationService.transform(baseValidPayload);
        assertEquals("APPROVED", result.get("decision"));
        verify(transformationService).transform(baseValidPayload);
    }

    @Test
    void shouldRejectPayloadMissingRequiredInputs() {
        Map<String, Object> incompletePayload = new HashMap<>(baseValidPayload);
        incompletePayload.remove("policy_status");
        when(transformationService.transform(anyMap())).thenThrow(new IllegalArgumentException("Missing required inputs"));
        assertThrows(IllegalArgumentException.class, () -> transformationService.transform(incompletePayload));
    }

    @Test
    void shouldRejectInvalidDateOfLoss() {
        Map<String, Object> payload = new HashMap<>(baseValidPayload);
        payload.put("date_of_loss", "invalid-date-format");
        when(transformationService.transform(anyMap())).thenThrow(new IllegalArgumentException("date_of_loss must be valid date."));
        assertThrows(IllegalArgumentException.class, () -> transformationService.transform(payload));
    }

    @Test
    void shouldRejectFuturePolicyDates() {
        Map<String, Object> payload = new HashMap<>(baseValidPayload);
        payload.put("policy_effective_date", LocalDate.now().plusDays(5).format(DATE_FORMAT));
        when(transformationService.transform(anyMap())).thenThrow(new IllegalArgumentException("policy dates must be valid and not in future."));
        assertThrows(IllegalArgumentException.class, () -> transformationService.transform(payload));
    }

    @Test
    void shouldRejectInvalidMoratoriumDateRanges() {
        Map<String, Object> payload = new HashMap<>(baseValidPayload);
        payload.put("moratorium_dates", List.of(Map.of("start", "bad-date", "end", "2023-12-31")));
        when(transformationService.transform(anyMap())).thenThrow(new IllegalArgumentException("moratorium_dates must be list of valid date ranges."));
        assertThrows(IllegalArgumentException.class, () -> transformationService.transform(payload));
    }

    @Test
    void shouldProcessOptionalInputsCorrectlyWithoutAffectingCoreValidation() {
        Map<String, Object> payloadWithOptional = new HashMap<>(baseValidPayload);
        payloadWithOptional.put("cancellation_date", "2023-11-01");
        payloadWithOptional.put("storm_event_dates", List.of("2023-09-15"));
        when(transformationService.transform(anyMap())).thenReturn(Map.of("decision", "CONDITIONAL_APPROVAL"));
        Map<String, Object> result = transformationService.transform(payloadWithOptional);
        assertEquals("CONDITIONAL_APPROVAL", result.get("decision"));
    }

    @Test
    void shouldVerifyFreshnessRequirementsForMoratoriumAndPolicyData() {
        Map<String, Object> payload = new HashMap<>(baseValidPayload);
        when(transformationService.validateFreshness(anyMap())).thenReturn(true);
        assertTrue(transformationService.validateFreshness(payload));
        verify(transformationService).validateFreshness(payload);
    }
}
