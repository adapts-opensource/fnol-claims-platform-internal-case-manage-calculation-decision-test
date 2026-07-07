package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationMockTest {

    @Mock
    private BusinessCalendarService businessCalendarService;

    @InjectMocks
    private RoutingDecisionCalculationService calculationService;

    @Test
    void intake_on_weekend_holiday() {
        // Arrange: Simulate weekend/holiday intake
        LocalDate weekendDate = LocalDate.of(2023, 10, 14);
        when(businessCalendarService.isWorkingDay(weekendDate)).thenReturn(false);

        Map<String, Object> payload = Map.of(
                "claimId", "CLM-98765",
                "intakeDate", weekendDate.toString(),
                "policyType", "AUTO"
        );

        // Act: Trigger decision calculation
        Map<String, Object> decision = calculationService.calculate(payload);

        // Assert: Verify routing behavior for non-working days
        assertNotNull(decision, "Routing decision must not be null");
        assertEquals("DEFERRED_PROCESSING", decision.get("processingStatus"));
        assertEquals("HOLIDAY_QUEUE", decision.get("targetQueue"));
        assertTrue((Integer) decision.get("slaExtensionDays") >= 1);
        verify(businessCalendarService).isWorkingDay(weekendDate);
    }
}
