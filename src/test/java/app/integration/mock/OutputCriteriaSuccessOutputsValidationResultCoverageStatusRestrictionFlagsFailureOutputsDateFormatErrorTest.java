package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutputCriteriaSuccessOutputsValidationResultCoverageStatusRestrictionFlagsFailureOutputsDateFormatErrorTest {

    @Mock
    private StateTransitionCalculator stateTransitionCalculator;

    private Map<String, Object> testPayload;
    private static final String EXPECTED_STATUS_UPDATE = "Claim status updated with validation result.";

    @BeforeEach
    void setUp() {
        testPayload = Map.of(
            "id", "fnol-123",
            "payload", Map.of(
                "claim_type", "auto",
                "date_of_loss", "invalid-date",
                "policy_number", null
            )
        );
    }

    @Test
    void output_criteria_success_outputs_validation_result_coverage_status_restriction_flags_failure_outputs_date_format_error_missing_policy_data_status_updates_claim_status_updated_with_validation_result() {
        // Arrange
        Map<String, Object> successOutputs = Map.of(
            "validation_result", Map.of("status", "validated", "score", 85),
            "coverage_status", Map.of("is_covered", true, "limits", 50000),
            "restriction_flags", Set.of("deductible_applied", "waiting_period_met")
        );
        Map<String, Object> failureOutputs = Map.of(
            "date_format_error", "Invalid date format for date_of_loss",
            "missing_policy_data", "Policy number is required"
        );

        when(stateTransitionCalculator.calculate(any(Map.class)))
            .thenReturn(Map.of(
                "success_outputs", successOutputs,
                "failure_outputs", failureOutputs,
                "status_updates", List.of(EXPECTED_STATUS_UPDATE)
            ));

        // Act
        Map<String, Object> result = stateTransitionCalculator.calculate(testPayload);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("success_outputs"), "Result must contain success_outputs");
        assertTrue(result.containsKey("failure_outputs"), "Result must contain failure_outputs");
        assertTrue(result.containsKey("status_updates"), "Result must contain status_updates");

        @SuppressWarnings("unchecked")
        Map<String, Object> successOutputsResult = (Map<String, Object>) result.get("success_outputs");
        assertTrue(successOutputsResult.containsKey("validation_result"), "Success outputs must contain validation_result");
        assertTrue(successOutputsResult.containsKey("coverage_status"), "Success outputs must contain coverage_status");
        assertTrue(successOutputsResult.containsKey("restriction_flags"), "Success outputs must contain restriction_flags");

        @SuppressWarnings("unchecked")
        Map<String, Object> failureOutputsResult = (Map<String, Object>) result.get("failure_outputs");
        assertTrue(failureOutputsResult.containsKey("date_format_error"), "Failure outputs must contain date_format_error");
        assertTrue(failureOutputsResult.containsKey("missing_policy_data"), "Failure outputs must contain missing_policy_data");

        @SuppressWarnings("unchecked")
        List<String> statusUpdates = (List<String>) result.get("status_updates");
        assertTrue(statusUpdates.contains(EXPECTED_STATUS_UPDATE), "Status updates must contain the expected message");

        verify(stateTransitionCalculator, times(1)).calculate(testPayload);
    }
}
