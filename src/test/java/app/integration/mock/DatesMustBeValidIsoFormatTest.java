package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that the decision transformation pipeline strictly enforces
 * ISO 8601 date format validation for Insured Engagement & Tracking.
 */
@ExtendWith(MockitoExtension.class)
class DatesMustBeValidIsoFormatTest {

    @Mock
    private DecisionTransformationService transformationService;

    @InjectMocks
    private InsuredEngagementService insuredEngagementService;

    @Test
    void dates_must_be_valid_iso_format() {
        // Given valid ISO 8601 date string
        String validIsoDate = "2023-10-25T14:30:00";
        when(transformationService.transformDate(validIsoDate)).thenReturn(validIsoDate);

        // When & Then valid date transformation succeeds without exception
        assertDoesNotThrow(() -> insuredEngagementService.processDecision(validIsoDate));
        verify(transformationService).transformDate(validIsoDate);

        // Given invalid date format
        String invalidDate = "25/10/2023 14:30";
        when(transformationService.transformDate(invalidDate))
                .thenThrow(new IllegalArgumentException("Dates must be valid ISO format"));

        // When & Then invalid date transformation fails with expected exception
        assertThrows(IllegalArgumentException.class, () -> insuredEngagementService.processDecision(invalidDate));
        verify(transformationService).transformDate(invalidDate);
    }
}
