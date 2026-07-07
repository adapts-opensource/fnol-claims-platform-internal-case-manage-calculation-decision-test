package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.HashMap;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class PurposeTransformFnolDataIntoGuidewireStyleModelTest {

    @Mock
    private GuidewireTransformationService transformationService;
    @Mock
    private DataStorePersistenceService persistenceService;
    @Mock
    private InputValidationService validationService;
    @Mock
    private StructuredLogger logger;

    private StateTransitionCalculationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new StateTransitionCalculationService(transformationService, persistenceService, validationService, logger);
    }

    @Test
    void purpose_transform_fnol_data_into_guidewire_style_model_and_persist() {
        // Arrange
        String fnolId = "fnol-abc-123";
        Map<String, Object> rawPayload = new HashMap<>();
        rawPayload.put("channel", "MOBILE");
        rawPayload.put("incidentDate", "2024-01-15");
        rawPayload.put("policyNumber", "POL-998877");

        Map<String, Object> expectedGuidewireModel = new HashMap<>();
        expectedGuidewireModel.put("id", fnolId);
        expectedGuidewireModel.put("payload", rawPayload);
        expectedGuidewireModel.put("state", "CALCULATING");
        expectedGuidewireModel.put("riskScore", 82.5);
        expectedGuidewireModel.put("premiumEstimate", 1250.00);

        when(validationService.isValid(rawPayload)).thenReturn(true);
        when(transformationService.transformToGuidewireStyle(rawPayload)).thenReturn(expectedGuidewireModel);
        when(persistenceService.persist(fnolId, expectedGuidewireModel)).thenReturn("s3://fnol-intake-bucket/fnol-abc-123.json");

        // Act
        String resultUri = service.processStateTransition(fnolId, rawPayload);

        // Assert
        assertNotNull(resultUri);
        assertEquals("s3://fnol-intake-bucket/fnol-abc-123.json", resultUri);

        verify(validationService).isValid(rawPayload);
        verify(transformationService).transformToGuidewireStyle(rawPayload);
        verify(persistenceService).persist(fnolId, expectedGuidewireModel);
        verify(logger).info("FNOL state transition completed", "fnolId", fnolId, "targetState", "CALCULATING");
    }
}
