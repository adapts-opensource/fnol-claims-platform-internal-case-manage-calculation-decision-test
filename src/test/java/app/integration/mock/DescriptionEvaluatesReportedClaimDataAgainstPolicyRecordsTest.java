package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationValidationTest {

    @Mock
    private PolicyRecordService policyRecordService;

    @Mock
    private TemporalCoverageValidator temporalCoverageValidator;

    @Mock
    private MoratoriumRestrictionChecker moratoriumRestrictionChecker;

    @Mock
    private HandlingPathClassifier handlingPathClassifier;

    @InjectMocks
    private ClaimDataStandardizationValidationService claimDataStandardizationValidationService;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimPayload = Map.of(
                "id", "CLM-2024-001",
                "policyId", "POL-8842",
                "lossDate", "2024-09-12",
                "causeOfLoss", "WINDSTORM",
                "productForm", "HO-3_STANDARD",
                "reportedAmount", 25000.00
        );
    }

    @Test
    void description_evaluates_reported_claim_data_against_policy_records_using_deterministic_matching_rules_validates_temporal_coverage_windows_checks_for_moratoriums_storm_restrictions_and_classifies_the_claim_into_a_handling_path_based_on_product_form_and_cause_of_loss() {
        // Arrange
        String policyId = (String) claimPayload.get("policyId");
        LocalDate lossDate = LocalDate.parse((String) claimPayload.get("lossDate"));
        String causeOfLoss = (String) claimPayload.get("causeOfLoss");
        String productForm = (String) claimPayload.get("productForm");

        Map<String, Object> policyRecord = Map.of(
                "policyId", policyId,
                "effectiveDate", "2024-01-01",
                "expirationDate", "2025-01-01",
                "coverageType", "STRUCTURE_AND_CONTENT"
        );
        when(policyRecordService.findByPolicyId(eq(policyId))).thenReturn(Optional.of(policyRecord));

        when(temporalCoverageValidator.validateWindow(eq(policyId), eq(lossDate))).thenReturn(true);
        when(moratoriumRestrictionChecker.checkRestrictions(eq(causeOfLoss), any())).thenReturn(false);
        when(handlingPathClassifier.classify(eq(productForm), eq(causeOfLoss))).thenReturn("STANDARD_AUTOMATED_REVIEW");

        // Act
        Map<String, Object> validationResult = claimDataStandardizationValidationService.evaluateAndClassify(claimPayload);

        // Assert
        assertNotNull(validationResult);
        assertEquals("VALID", validationResult.get("validationStatus"));
        assertTrue((Boolean) validationResult.get("temporalCoverageValid"));
        assertFalse((Boolean) validationResult.get("isMoratoriumRestricted"));
        assertEquals("STANDARD_AUTOMATED_REVIEW", validationResult.get("handlingPath"));

        // Verify deterministic matching and rule application
        verify(policyRecordService, times(1)).findByPolicyId(eq(policyId));
        verify(temporalCoverageValidator, times(1)).validateWindow(eq(policyId), eq(lossDate));
        verify(moratoriumRestrictionChecker, times(1)).checkRestrictions(eq(causeOfLoss), any());
        verify(handlingPathClassifier, times(1)).classify(eq(productForm), eq(causeOfLoss));
    }
}
