package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Test class for Insured Engagement & Tracking: decision: transformation.
 * Verifies Date of Loss handling during the transformation process.
 */
public class InsuredEngagementTrackingDecisionTransformationTest {

    @InjectMocks
    private EngagementTransformationService engagementTransformationService;

    @Mock
    private DataPersistenceService dataPersistenceService;

    @Mock
    private ComplianceValidationService complianceValidationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void dateOfLoss117() {
        // Arrange
        String testCaseLabel = "Date_of_loss";
        String rawDateInput = "2024-08-12";
        LocalDate expectedDate = LocalDate.parse(rawDateInput);
        String exposureId = "EXP-117-TRANSFORM";

        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("exposureId", exposureId);
        inputPayload.put("dateOfLoss", rawDateInput);
        inputPayload.put("claimId", "CLM-TEST-001");
        inputPayload.put("status", "INITIATED");

        // Mock external I/O: DynamoDB persistence
        when(dataPersistenceService.fetchExposureData(exposureId))
            .thenReturn(inputPayload);

        // Mock external I/O: Compliance/Validation service
        when(complianceValidationService.validateDateOfLoss(rawDateInput))
            .thenReturn(true);

        // Act
        var transformationResult = engagementTransformationService.transformDecisionPayload(inputPayload);

        // Assert
        assertNotNull(transformationResult, "Transformation result must not be null");
        assertEquals(expectedDate, transformationResult.getDateOfLoss(), 
            "Date of loss should be correctly parsed and transformed");
        assertEquals(exposureId, transformationResult.getExposureId(), 
            "Exposure ID should be preserved during transformation");
        assertTrue(transformationResult.isComplianceChecked(), 
            "Compliance check flag should be set to true");
        assertEquals("TRANSFORMED", transformationResult.getCurrentStage(), 
            "Stage should update after transformation");

        // Verify interactions
        verify(dataPersistenceService, times(1)).fetchExposureData(exposureId);
        verify(complianceValidationService, times(1)).validateDateOfLoss(rawDateInput);
    }
}
