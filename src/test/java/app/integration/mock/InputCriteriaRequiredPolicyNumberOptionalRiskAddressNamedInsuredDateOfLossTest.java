package app.integration.mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import app.domain.claim.ClaimInitiationPayload;
import app.domain.claim.RoutingDecision;
import app.domain.policy.PolicyData;
import app.domain.validation.ValidationResult;
import app.infrastructure.client.PasClient;
import app.infrastructure.validation.InputValidator;
import app.service.claim.ClaimInitiationOrchestrationService;
import app.util.DateUtils;

/**
 * Integration mock tests for Claim Initiation & Routing: Orchestration Decision.
 * Validates input criteria, format normalization, freshness requirements, and routing logic.
 */
@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionValidationTest {

    private static final String POLICY_NUMBER = "POL-2024-001";
    private static final String RISK_ADDRESS = "123 Main St, Springfield, IL, 62704";
    private static final String NAMED_INSURED = "John Doe";
    private static final LocalDate DATE_OF_LOSS = LocalDate.of(2024, 10, 27);
    private static final String PRODUCT_FORM = "HO-3";
    private static final String OCCUPANCY_TYPE = "OWNER_OCCUPIED";
    private static final String AGENT_ID = "AGT-555";

    @Mock
    private PasClient pasClient;

    @Mock
    private InputValidator inputValidator;

    @Spy
    @InjectMocks
    private ClaimInitiationOrchestrationService orchestrationService;

    private ClaimInitiationPayload validPayload;

    @BeforeEach
    void setUp() {
        validPayload = ClaimInitiationPayload.builder()
                .policyNumber(POLICY_NUMBER)
                .riskAddress(RISK_ADDRESS)
                .namedInsured(NAMED_INSURED)
                .dateOfLoss(DATE_OF_LOSS)
                .productForm(PRODUCT_FORM)
                .occupancyType(OCCUPANCY_TYPE)
                .agentId(AGENT_ID)
                .tenantInfo(Map.of("leaseStart", "2023-01-01"))
                .build();
    }

    @Nested
    @DisplayName("Input Criteria & Validation Scenarios")
    class InputCriteriaTests {

        @Test
        @DisplayName("Input criteria required policy number optional risk address named insured date of loss product form occupancy type optional agent id tenant info input validation policy number format validation address normalization and validation date format validation named insured format validation freshness requirements policy data must be retrieved in real time from pas")
        void inputCriteriaRequiredPolicyNumberOptionalRiskAddressNamedInsuredDateOfLossProductFormOccupancyTypeOptionalAgentIdTenantInfoInputValidationPolicyNumberFormatValidationAddressNormalizationAndValidationDateFormatValidationNamedInsuredFormatValidationFreshnessRequirementsPolicyDataMustBeRetrievedInRealTimeFromPas() {
            // Given
            PolicyData pasData = PolicyData.builder()
                    .policyNumber(POLICY_NUMBER)
                    .status("ACTIVE")
                    .build();

            // Mock validation
            when(inputValidator.validatePolicyNumberFormat(POLICY_NUMBER)).thenReturn(ValidationResult.success());
            when(inputValidator.normalizeAndValidateAddress(RISK_ADDRESS)).thenReturn(RISK_ADDRESS); // Returns normalized
            when(inputValidator.validateDateFormat(DATE_OF_LOSS.toString())).thenReturn(ValidationResult.success());
            when(inputValidator.validateNamedInsuredFormat(NAMED_INSURED)).thenReturn(ValidationResult.success());

            // Mock PAS retrieval (Freshness requirement)
            when(pasClient.retrievePolicyDataInRealTime(POLICY_NUMBER)).thenReturn(pasData);

            // When
            RoutingDecision decision = orchestrationService.evaluateDecision(validPayload);

            // Then
            assertNotNull(decision, "Routing decision should not be null");
            assertEquals(RoutingDecision.Status.APPROVED, decision.getStatus(), "Should proceed to routing");
            
            // Verify freshness: Policy data must be retrieved in real-time from PAS
            verify(pasClient, times(1)).retrievePolicyDataInRealTime(POLICY_NUMBER);
            
            // Verify validations were invoked
            verify(inputValidator).validatePolicyNumberFormat(POLICY_NUMBER);
            verify(inputValidator).normalizeAndValidateAddress(RISK_ADDRESS);
            verify(inputValidator).validateDateFormat(DATE_OF_LOSS.toString());
            verify(inputValidator).validateNamedInsuredFormat(NAMED_INSURED);
        }

        @Test
        @DisplayName("Optional policy number should be handled gracefully when present")
        void optionalPolicyNumberPresentShouldValidateFormat() {
            // Given
            ClaimInitiationPayload payloadWithPolicy = ClaimInitiationPayload.builder()
                    .policyNumber(POLICY_NUMBER)
                    .riskAddress(RISK_ADDRESS)
                    .namedInsured(NAMED_INSURED)
                    .dateOfLoss(DATE_OF_LOSS)
                    .productForm(PRODUCT_FORM)
                    .occupancyType(OCCUPANCY_TYPE)
                    .build();

            when(inputValidator.validatePolicyNumberFormat(POLICY_NUMBER)).thenReturn(ValidationResult.success());
            when(inputValidator.normalizeAndValidateAddress(RISK_ADDRESS)).thenReturn(RISK_ADDRESS);
            when(inputValidator.validateDateFormat(DATE_OF_LOSS.toString())).thenReturn(ValidationResult.success());
            when(inputValidator.validateNamedInsuredFormat(NAMED_INSURED)).thenReturn(ValidationResult.success());
            when(pasClient.retrievePolicyDataInRealTime(POLICY_NUMBER)).thenReturn(PolicyData.builder().status("ACTIVE").build());

            // When / Then
            assertDoesNotThrow(() -> orchestrationService.evaluateDecision(payloadWithPolicy));
            verify(inputValidator).validatePolicyNumberFormat(POLICY_NUMBER);
        }

        @Test
        @DisplayName("Invalid policy number format should trigger validation failure")
        void invalidPolicyNumberFormatShouldFailValidation() {
            // Given
            ClaimInitiationPayload invalidPayload = ClaimInitiationPayload.builder()
                    .policyNumber("INVALID-POL")
                    .riskAddress(RISK_ADDRESS)
                    .namedInsured(NAMED_INSURED)
                    .dateOfLoss(DATE_OF_LOSS)
                    .productForm(PRODUCT_FORM)
                    .occupancyType(OCCUPANCY_TYPE)
                    .build();

            when(inputValidator.validatePolicyNumberFormat("INVALID-POL")).thenReturn(ValidationResult.failure("Invalid format"));

            // When / Then
            assertThrows(IllegalArgumentException.class, () -> orchestrationService.evaluateDecision(invalidPayload));
            verify(inputValidator).validatePolicyNumberFormat("INVALID-POL");
            verifyNoInteractions(pasClient); // Should not call PAS if validation fails early
        }
    }
}
