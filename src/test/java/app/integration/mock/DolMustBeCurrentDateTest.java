package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DolMustBeCurrentDateTest {

    @Mock
    private DecisionTransformationValidator mockDecisionValidator;

    private InsuredEngagementTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = new InsuredEngagementTransformationService(mockDecisionValidator);
    }

    @Test
    void dol_must_be_current_date() {
        LocalDate currentDate = LocalDate.now();
        LocalDate validPastDate = currentDate.minusDays(15);

        // Mock external validation service to return success for DOL <= current date
        when(mockDecisionValidator.isWithinValidRange(any(LocalDate.class))).thenReturn(true);

        // Verify current date passes transformation
        assertDoesNotThrow(() -> transformationService.processDol(currentDate));
        verify(mockDecisionValidator).isWithinValidRange(currentDate);

        // Verify historical date passes transformation
        assertDoesNotThrow(() -> transformationService.processDol(validPastDate));
        verify(mockDecisionValidator).isWithinValidRange(validPastDate);
    }
}
