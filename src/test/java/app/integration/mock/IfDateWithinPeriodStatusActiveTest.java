package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementDecisionOrchestratorMockTest {

    @Mock
    private PeriodValidationService periodValidationService;

    @Test
    void ifDateWithinPeriodStatusActive() {
        // Arrange
        LocalDate asOfDate = LocalDate.of(2024, 5, 15);
        LocalDate periodStart = LocalDate.of(2024, 1, 1);
        LocalDate periodEnd = LocalDate.of(2024, 12, 31);

        when(periodValidationService.isWithinPeriod(asOfDate, periodStart, periodEnd)).thenReturn(true);

        EngagementDecisionOrchestrator orchestrator = new EngagementDecisionOrchestrator(periodValidationService);

        // Act
        String decisionStatus = orchestrator.determineEngagementStatus(asOfDate, periodStart, periodEnd);

        // Assert
        assertEquals("Active", decisionStatus, "Status should be Active when date is within the defined period");
        verify(periodValidationService).isWithinPeriod(asOfDate, periodStart, periodEnd);
    }

    // Simplified domain/service stubs for self-contained test execution
    interface PeriodValidationService {
        boolean isWithinPeriod(LocalDate asOfDate, LocalDate periodStart, LocalDate periodEnd);
    }

    static class EngagementDecisionOrchestrator {
        private final PeriodValidationService periodValidationService;

        EngagementDecisionOrchestrator(PeriodValidationService periodValidationService) {
            this.periodValidationService = periodValidationService;
        }

        String determineEngagementStatus(LocalDate asOfDate, LocalDate periodStart, LocalDate periodEnd) {
            if (periodValidationService.isWithinPeriod(asOfDate, periodStart, periodEnd)) {
                return "Active";
            }
            return "Inactive";
        }
    }
}
