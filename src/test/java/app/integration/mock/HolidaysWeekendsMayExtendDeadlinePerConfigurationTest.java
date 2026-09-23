package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationTransformationTest {

    private static final Logger LOGGER = Logger.getLogger(ClaimInitiationTransformationTest.class.getName());

    @Mock
    private DeadlineConfigurationProvider configProvider;

    @Mock
    private BusinessCalendarService businessCalendar;

    private ClaimTransformationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimTransformationOrchestrator(configProvider, businessCalendar, LOGGER);
    }

    @Test
    void holidays_weekends_may_extend_deadline_per_configuration() {
        // Given
        LocalDate initialDeadline = LocalDate.of(2024, 5, 4); // Saturday
        LocalDate expectedExtendedDeadline = LocalDate.of(2024, 5, 6); // Monday

        when(configProvider.isDeadlineExtensionEnabled()).thenReturn(true);
        when(configProvider.getExtensionRule()).thenReturn(ExtensionRule.WEEKEND_HOLIDAY);
        when(businessCalendar.getNextBusinessDay(initialDeadline)).thenReturn(expectedExtendedDeadline);

        // When
        LocalDate actualDeadline = orchestrator.calculateDeadline(initialDeadline);

        // Then
        assertEquals(expectedExtendedDeadline, actualDeadline);
        verify(configProvider).isDeadlineExtensionEnabled();
        verify(configProvider).getExtensionRule();
        verify(businessCalendar).getNextBusinessDay(initialDeadline);
    }

    // Minimal supporting types for standalone compilation
    interface DeadlineConfigurationProvider {
        boolean isDeadlineExtensionEnabled();
        ExtensionRule getExtensionRule();
    }

    interface BusinessCalendarService {
        LocalDate getNextBusinessDay(LocalDate date);
    }

    enum ExtensionRule {
        NONE, WEEKEND_HOLIDAY, FIXED_DAYS
    }

    static class ClaimTransformationOrchestrator {
        private final DeadlineConfigurationProvider configProvider;
        private final BusinessCalendarService businessCalendar;
        private final Logger logger;

        ClaimTransformationOrchestrator(DeadlineConfigurationProvider configProvider,
                                        BusinessCalendarService businessCalendar,
                                        Logger logger) {
            this.configProvider = configProvider;
            this.businessCalendar = businessCalendar;
            this.logger = logger;
        }

        LocalDate calculateDeadline(LocalDate initialDeadline) {
            logger.log(Level.INFO, "Starting deadline transformation for initial date: {0}", initialDeadline);
            if (configProvider.isDeadlineExtensionEnabled() && configProvider.getExtensionRule() == ExtensionRule.WEEKEND_HOLIDAY) {
                LocalDate extended = businessCalendar.getNextBusinessDay(initialDeadline);
                logger.log(Level.INFO, "Deadline extended per configuration to: {0}", extended);
                return extended;
            }
            logger.log(Level.INFO, "No extension applied, returning original deadline: {0}", initialDeadline);
            return initialDeadline;
        }
    }
}
