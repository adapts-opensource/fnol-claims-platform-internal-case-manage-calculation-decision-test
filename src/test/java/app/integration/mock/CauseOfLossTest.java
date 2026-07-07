package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CauseOfLossTransformationTest {

    @Mock
    private ClaimEngagementService claimEngagementService;

    private String validCauseOfLoss;
    private String expectedTransformedValue;

    @BeforeEach
    void setUp() {
        validCauseOfLoss = "FIRE";
        expectedTransformedValue = "Fire Event";
    }

    @Test
    void cause_of_loss() {
        when(claimEngagementService.transformCauseOfLoss(validCauseOfLoss)).thenReturn(expectedTransformedValue);

        String result = claimEngagementService.transformCauseOfLoss(validCauseOfLoss);

        assertEquals(expectedTransformedValue, result);
        verify(claimEngagementService, times(1)).transformCauseOfLoss(validCauseOfLoss);
    }

    @Test
    void causeOfLoss_nullInput_shouldThrowValidationException() {
        when(claimEngagementService.transformCauseOfLoss(null)).thenThrow(new IllegalArgumentException("Cause of loss cannot be null"));

        assertThrows(IllegalArgumentException.class, () -> claimEngagementService.transformCauseOfLoss(null));
    }

    @Test
    void causeOfLoss_emptyInput_shouldThrowValidationException() {
        when(claimEngagementService.transformCauseOfLoss("")).thenThrow(new IllegalArgumentException("Cause of loss cannot be empty"));

        assertThrows(IllegalArgumentException.class, () -> claimEngagementService.transformCauseOfLoss(""));
    }
}
