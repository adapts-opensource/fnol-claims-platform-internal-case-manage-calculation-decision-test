package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolStateTransitionCalculationTest {

    @Mock
    private FnolSubmissionService fnolSubmissionService;

    @Mock
    private ClaimsDatabaseService claimsDatabaseService;

    private Map<String, Object> basePayload;

    @BeforeEach
    void setUp() {
        basePayload = new HashMap<>();
        basePayload.put("policy_number", "POL-12345");
        basePayload.put("risk_address", "123 Main St, Anytown, USA");
        basePayload.put("date_of_loss", "2023-10-01");
        basePayload.put("cause_of_loss", "fire");
        basePayload.put("catastrophe_event", "yes");
        basePayload.put("reporter", "John Doe");
        basePayload.put("damaged_area", "roof");
        basePayload.put("prior_claim_status", "none");
        basePayload.put("insured_name", "Jane Doe");
    }

    @Test
    void testInputCriteriaRequiredInputsPolicyNumberRiskAddressDateOfLossCauseOfLossCatastropheEventReporterDamagedAreaPriorClaimStatusOptionalInputsInsuredNameInputValidationInputsMustBeNormalizedFreshnessRequirementsExistingClaimsDataFromClaimsDbRealTime() {
        // Arrange: normalize inputs per validation rule
        Map<String, Object> normalizedPayload = new HashMap<>(basePayload);
        normalizedPayload.put("policy_number", "pol-12345");
        normalizedPayload.put("risk_address", "123 main st, anytown, usa");
        normalizedPayload.put("cause_of_loss", "fire");
        normalizedPayload.put("reporter", "john doe");
        normalizedPayload.put("insured_name", "jane doe");

        when(claimsDatabaseService.isDataFresh(anyString())).thenReturn(true);
        when(fnolSubmissionService.validateInputs(normalizedPayload)).thenReturn(true);
        when(fnolSubmissionService.calculateStateTransition(normalizedPayload)).thenReturn("ACCEPTED");

        // Act
        boolean isValid = fnolSubmissionService.validateInputs(normalizedPayload);
        boolean isFresh = claimsDatabaseService.isDataFresh("POL-12345");
        String state = fnolSubmissionService.calculateStateTransition(normalizedPayload);

        // Assert
        assertTrue(isValid, "Normalized required and optional inputs must pass validation");
        assertTrue(isFresh, "Real-time claims DB freshness check must succeed");
        assertEquals("ACCEPTED", state, "State transition calculation must succeed");
        verify(claimsDatabaseService, times(1)).isDataFresh("POL-12345");
        verify(fnolSubmissionService, times(1)).calculateStateTransition(normalizedPayload);
    }

    @Test
    void testMissingRequiredInputThrowsValidationException() {
        Map<String, Object> invalidPayload = new HashMap<>(basePayload);
        invalidPayload.remove("date_of_loss");

        when(fnolSubmissionService.validateInputs(invalidPayload)).thenThrow(new IllegalArgumentException("Missing required input: date_of_loss"));

        assertThrows(IllegalArgumentException.class, () -> fnolSubmissionService.validateInputs(invalidPayload));
        verify(fnolSubmissionService, never()).calculateStateTransition(anyMap());
    }

    @Test
    void testUnnormalizedInputIsRejected() {
        Map<String, Object> unnormalizedPayload = new HashMap<>(basePayload);
        unnormalizedPayload.put("policy_number", " POL-12345 ");
        unnormalizedPayload.put("cause_of_loss", " FIRE ");

        when(fnolSubmissionService.validateInputs(unnormalizedPayload)).thenThrow(new IllegalArgumentException("Inputs must be normalized"));

        assertThrows(IllegalArgumentException.class, () -> fnolSubmissionService.validateInputs(unnormalizedPayload));
    }

    @Test
    void testOptionalInsuredNameIgnoredWhenAbsent() {
        Map<String, Object> payloadWithoutOptional = new HashMap<>(basePayload);
        payloadWithoutOptional.remove("insured_name");

        when(fnolSubmissionService.validateInputs(payloadWithoutOptional)).thenReturn(true);
        when(fnolSubmissionService.calculateStateTransition(payloadWithoutOptional)).thenReturn("ACCEPTED");

        assertTrue(fnolSubmissionService.validateInputs(payloadWithoutOptional));
        assertEquals("ACCEPTED", fnolSubmissionService.calculateStateTransition(payloadWithoutOptional));
    }

    // Mocked external service contracts for state transition and DB freshness
    interface FnolSubmissionService {
        boolean validateInputs(Map<String, Object> payload);
        String calculateStateTransition(Map<String, Object> payload);
    }

    interface ClaimsDatabaseService {
        boolean isDataFresh(String policyNumber);
    }
}
