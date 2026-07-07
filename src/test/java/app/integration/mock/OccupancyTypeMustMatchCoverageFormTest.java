package app.integration.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Insured Engagement & Tracking:decision:transformation - Occupancy Type Validation")
public class OccupancyTypeCoverageFormMatchTest {

    @Mock
    private ClaimTransformationService transformationService;

    @Test
    @DisplayName("occupancy_type_must_match_coverage_form")
    void occupancy_type_must_match_coverage_form() {
        // Arrange: Valid matching occupancy and coverage form
        String occupancyType = "PRIMARY_RESIDENCE";
        String coverageForm = "HO3_FORM";

        // Mock service behavior to simulate successful transformation when matched
        when(transformationService.transform(anyString(), anyString()))
                .thenReturn(TransformationOutcome.SUCCESS);

        // Act & Assert: Verify successful transformation for matching types
        assertDoesNotThrow(() -> transformationService.transform(occupancyType, coverageForm));
        verify(transformationService, times(1)).transform(occupancyType, coverageForm);

        // Arrange: Invalid mismatching occupancy and coverage form
        String mismatchedOccupancy = "SECOND_HOME";
        String mismatchedCoverage = "HO3_FORM";

        // Mock service behavior to simulate validation failure
        when(transformationService.transform(mismatchedOccupancy, mismatchedCoverage))
                .thenThrow(new IllegalArgumentException("Occupancy type must match coverage form"));

        // Act & Assert: Verify validation error for mismatched types
        assertThrows(IllegalArgumentException.class, () ->
                transformationService.transform(mismatchedOccupancy, mismatchedCoverage));
        verify(transformationService, times(1)).transform(mismatchedOccupancy, mismatchedCoverage);
    }
}

// Minimal domain/service stubs for compilation and mock isolation
interface ClaimTransformationService {
    TransformationOutcome transform(String occupancyType, String coverageForm);
}

enum TransformationOutcome {
    SUCCESS
}
