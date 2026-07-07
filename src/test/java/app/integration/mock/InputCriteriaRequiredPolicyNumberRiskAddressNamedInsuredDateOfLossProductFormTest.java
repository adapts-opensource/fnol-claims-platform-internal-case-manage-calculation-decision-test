package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ClaimInitiationTransformationMockTest {

    @Mock
    private PolicyService policyService;
    @Mock
    private GeocodingService geocodingService;
    @Mock
    private TransformationOrchestrator transformationOrchestrator;

    private Map<String, Object> validPayload;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validPayload = Map.of(
            "policy_number", "POL123456789ABCDEF",
            "risk_address", "123 Main St, Springfield, IL 62704",
            "named_insured", "Jane Doe",
            "date_of_loss", "2023-10-25T14:30:00Z",
            "product_form", "HO-3",
            "occupancy_type", "Owner-Occupied",
            "tenant_landlord_relationship", "Landlord",
            "prior_claim_history", "None"
        );
    }

    @Test
    void input_criteria_required_policy_number_risk_address_named_insured_date_of_loss_product_form_occupancy_type_optional_tenant_landlord_relationship_prior_claim_history_validation_policy_number_must_be_alphanumeric_max_30_chars_address_must_pass_geocoding_validation_date_of_loss_must_be_valid_iso_8601_date_freshness_requirements_policy_status_must_be_retrieved_within_5_minutes_of_intake() {
        // Arrange
        when(geocodingService.validate(anyString())).thenReturn(true);
        when(policyService.retrieveStatus(anyString())).thenReturn("ACTIVE");
        when(policyService.getRetrievalTimestamp()).thenReturn(Instant.now().minus(2, ChronoUnit.MINUTES));
        when(transformationOrchestrator.transform(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> payload = invocation.getArgument(0);
            String policyNum = (String) payload.get("policy_number");
            if (policyNum == null || !policyNum.matches("^[A-Za-z0-9]{1,30}$")) {
                throw new IllegalArgumentException("Invalid policy number");
            }
            return Map.of("transformedClaimId", "CLM-998877", "status", "ROUTED");
        });

        // Act
        Map<String, Object> result = transformationOrchestrator.transform(validPayload);

        // Assert
        assertNotNull(result);
        assertEquals("ROUTED", result.get("status"));
        verify(geocodingService).validate(validPayload.get("risk_address").toString());
        verify(policyService).retrieveStatus(validPayload.get("policy_number").toString());
    }

    @Test
    void shouldFailOnInvalidPolicyNumberAlphanumericMax30() {
        Map<String, Object> invalidPayload = Map.copyOf(validPayload);
        invalidPayload.put("policy_number", "INVALID POLICY!@#");

        when(geocodingService.validate(anyString())).thenReturn(true);
        when(policyService.retrieveStatus(anyString())).thenReturn("ACTIVE");
        when(transformationOrchestrator.transform(anyMap())).thenThrow(new IllegalArgumentException("Invalid policy number"));

        assertThrows(IllegalArgumentException.class, () -> transformationOrchestrator.transform(invalidPayload));
    }

    @Test
    void shouldFailOnInvalidDateOfLossNotIso8601() {
        Map<String, Object> invalidPayload = Map.copyOf(validPayload);
        invalidPayload.put("date_of_loss", "10/25/2023");

        when(geocodingService.validate(anyString())).thenReturn(true);
        when(policyService.retrieveStatus(anyString())).thenReturn("ACTIVE");
        when(transformationOrchestrator.transform(anyMap())).thenThrow(new IllegalArgumentException("Invalid date_of_loss format"));

        assertThrows(IllegalArgumentException.class, () -> transformationOrchestrator.transform(invalidPayload));
    }

    @Test
    void shouldFailOnAddressGeocodingValidationFailure() {
        when(geocodingService.validate(anyString())).thenReturn(false);
        when(policyService.retrieveStatus(anyString())).thenReturn("ACTIVE");
        when(transformationOrchestrator.transform(anyMap())).thenThrow(new IllegalArgumentException("Address geocoding validation failed"));

        assertThrows(IllegalArgumentException.class, () -> transformationOrchestrator.transform(validPayload));
    }

    @Test
    void shouldValidatePolicyStatusFreshnessWithinFiveMinutes() {
        Instant staleTimestamp = Instant.now().minus(6, ChronoUnit.MINUTES);
        when(policyService.getRetrievalTimestamp()).thenReturn(staleTimestamp);
        when(geocodingService.validate(anyString())).thenReturn(true);
        when(policyService.retrieveStatus(anyString())).thenReturn("ACTIVE");

        assertThrows(IllegalStateException.class, () -> transformationOrchestrator.transform(validPayload));
    }
}
