package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class InsuredEngagementTransformationTest {

    private DecisionTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = mock(DecisionTransformationService.class);
    }

    @Test
    void dol_during_declared_catastrophe_with_moratorium() {
        // Arrange
        String dolDate = "2023-10-15";
        String catastropheId = "CAT_MORATOR_001";
        String insuredId = "INS_998877";

        // Mock external catastrophe & moratorium status checks
        when(transformationService.isDeclaredCatastrophe(dolDate, catastropheId)).thenReturn(true);
        when(transformationService.isMoratoriumActive(catastropheId)).thenReturn(true);

        // Act
        DecisionResult result = transformationService.transformDolEvent(insuredId, dolDate, catastropheId);

        // Assert
        assertNotNull(result, "Transformation result should not be null");
        assertEquals(DecisionStatus.MORATORIUM_HOLD, result.getStatus(),
                "DOL during catastrophe with active moratorium should trigger a hold");
        assertTrue(result.isDelayedProcessing(),
                "Event should be queued for delayed processing");
        assertFalse(result.isImmediatePayout(),
                "Immediate payout must be disabled during moratorium");
        verify(transformationService, times(1))
                .isDeclaredCatastrophe(dolDate, catastropheId);
        verify(transformationService, times(1))
                .isMoratoriumActive(catastropheId);
        verifyNoMoreInteractions(transformationService);
    }

    // Minimal service interface for mock context
    interface DecisionTransformationService {
        boolean isDeclaredCatastrophe(String dolDate, String catastropheId);
        boolean isMoratoriumActive(String catastropheId);
        DecisionResult transformDolEvent(String insuredId, String dolDate, String catastropheId);
    }

    // Minimal result record for mock context
    record DecisionResult(DecisionStatus status, boolean delayedProcessing, boolean immediatePayout) {}
}
