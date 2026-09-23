package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurposeValidateDateOfLossAgainstPolicyEffective")
public class PurposeValidateDateOfLossAgainstPolicyEffectiveTest {

    @Mock
    private PolicyValidationService policyValidationService;

    @InjectMocks
    private ClaimDataStandardizationCalculationTransformService transformService;

    @BeforeEach
    void setUp() {
        // Test fixture initialization handled by MockitoExtension
    }

    @Test
    @DisplayName("purpose_validate_date_of_loss_against_policy_effective_expiration_dates_cancellations_reinstatements_and_catastrophe_moratoriums")
    void purpose_validate_date_of_loss_against_policy_effective_expiration_dates_cancellations_reinstatements_and_catastrophe_moratoriums() {
        // Arrange
        String claimId = "claim-std-001";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("dateOfLoss", LocalDate.of(2023, 6, 15));
        inputPayload.put("policyEffectiveDate", LocalDate.of(2023, 1, 1));
        inputPayload.put("policyExpirationDate", LocalDate.of(2023, 12, 31));
        inputPayload.put("cancellationDate", null);
        inputPayload.put("reinstatementDate", null);
        inputPayload.put("catastropheMoratoriumActive", false);

        Map<String, Object> mockPolicyData = new HashMap<>();
        mockPolicyData.put("status", "ACTIVE");
        mockPolicyData.put("effectiveDate", LocalDate.of(2023, 1, 1));
        mockPolicyData.put("expirationDate", LocalDate.of(2023, 12, 31));
        mockPolicyData.put("cancellationDate", null);
        mockPolicyData.put("reinstatementDate", null);
        mockPolicyData.put("catastropheMoratoriumActive", false);

        when(policyValidationService.fetchPolicyData(claimId)).thenReturn(mockPolicyData);

        // Act
        Map<String, Object> transformedPayload = transformService.transformClaimData(claimId, inputPayload);

        // Assert
        assertNotNull(transformedPayload, "Transformed payload must not be null");
        assertTrue((Boolean) transformedPayload.get("dateOfLossValid"), "Date of loss should be valid against policy effective/expiration dates");
        assertEquals("ACTIVE", transformedPayload.get("policyStatus"), "Policy status should match resolved policy data");
        assertFalse((Boolean) transformedPayload.get("catastropheMoratoriumBlocked"), "Catastrophe moratorium should not block valid claims");
        verify(policyValidationService, times(1)).fetchPolicyData(claimId);
    }
}
