package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationTransformationValidationTest {

    @Mock
    private ChannelConfigService channelConfigService;
    @Mock
    private StatutoryTableService statutoryTableService;
    @Mock
    private HolidayCalendarService holidayCalendarService;

    private ClaimInitiationTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = new ClaimInitiationTransformationService(
                channelConfigService, statutoryTableService, holidayCalendarService);
    }

    @Test
    void validInputWithIsoTimestampsAndConfiguredDeadline_Succeeds() {
        String validIsoTimestamp = "2023-10-25T14:30:00Z";
        Map<String, Object> payload = Map.of(
                "fnol_submission_timestamp", validIsoTimestamp,
                "acknowledgment_timestamp", validIsoTimestamp,
                "channel_type", "WEB",
                "statutory_deadline_hours", 48
        );

        when(channelConfigService.getDeadlineHours("WEB")).thenReturn(48);
        when(statutoryTableService.isCurrent("STATUTORY_TABLE_V1")).thenReturn(true);
        when(holidayCalendarService.isCurrent("HOLIDAY_2023")).thenReturn(true);

        assertDoesNotThrow(() -> transformationService.validateAndTransform(payload));
        verify(channelConfigService, times(1)).getDeadlineHours("WEB");
    }

    @Test
    void invalidTimestampFormat_ThrowsValidationException() {
        Map<String, Object> payload = Map.of(
                "fnol_submission_timestamp", "not-an-iso-date",
                "acknowledgment_timestamp", "2023-10-25T14:30:00Z",
                "channel_type", "WEB",
                "statutory_deadline_hours", 48
        );
        when(channelConfigService.getDeadlineHours("WEB")).thenReturn(48);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> transformationService.validateAndTransform(payload));
        assertTrue(exception.getMessage().contains("fnol_submission_timestamp"));
        assertTrue(exception.getMessage().contains("ISO-8601"));
    }

    @Test
    void missingRequiredField_ThrowsValidationException() {
        Map<String, Object> payload = Map.of(
                "fnol_submission_timestamp", "2023-10-25T14:30:00Z",
                "acknowledgment_timestamp", "2023-10-25T14:30:00Z",
                // channel_type intentionally missing
                "statutory_deadline_hours", 48
        );
        when(channelConfigService.getDeadlineHours(anyString())).thenReturn(48);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> transformationService.validateAndTransform(payload));
        assertTrue(exception.getMessage().contains("Missing required field"));
    }

    @Test
    void unconfiguredChannelDeadline_ThrowsValidationException() {
        Map<String, Object> payload = Map.of(
                "fnol_submission_timestamp", "2023-10-25T14:30:00Z",
                "acknowledgment_timestamp", "2023-10-25T14:30:00Z",
                "channel_type", "UNKNOWN_CHANNEL",
                "statutory_deadline_hours", 48
        );
        when(channelConfigService.getDeadlineHours("UNKNOWN_CHANNEL")).thenReturn(null);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> transformationService.validateAndTransform(payload));
        assertTrue(exception.getMessage().contains("Deadline must be configured per channel"));
    }

    @Test
    void optionalFieldsWithFreshnessCheck_PassesValidation() {
        Map<String, Object> payload = Map.of(
                "fnol_submission_timestamp", "2023-10-25T14:30:00Z",
                "acknowledgment_timestamp", "2023-10-25T14:30:00Z",
                "channel_type", "WEB",
                "statutory_deadline_hours", 48,
                "holiday_calendar", "HOLIDAY_2023",
                "business_hours_schedule", "SCHEDULE_A"
        );
        when(channelConfigService.getDeadlineHours("WEB")).thenReturn(48);
        when(holidayCalendarService.isCurrent("HOLIDAY_2023")).thenReturn(true);
        when(statutoryTableService.isCurrent("STATUTORY_TABLE_V1")).thenReturn(true);

        assertDoesNotThrow(() -> transformationService.validateAndTransform(payload));
        verify(holidayCalendarService, times(1)).isCurrent("HOLIDAY_2023");
    }

    @Test
    void staleStatutoryTable_ThrowsValidationException() {
        Map<String, Object> payload = Map.of(
                "fnol_submission_timestamp", "2023-10-25T14:30:00Z",
                "acknowledgment_timestamp", "2023-10-25T14:30:00Z",
                "channel_type", "WEB",
                "statutory_deadline_hours", 48
        );
        when(channelConfigService.getDeadlineHours("WEB")).thenReturn(48);
        when(statutoryTableService.isCurrent("STATUTORY_TABLE_V1")).thenReturn(false);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> transformationService.validateAndTransform(payload));
        assertTrue(exception.getMessage().contains("Statutory tables must be current"));
    }

    // Internal simulation classes for test isolation
    static class ValidationException extends RuntimeException {
        ValidationException(String msg) { super(msg); }
    }

    interface ChannelConfigService { Integer getDeadlineHours(String channel); }
    interface StatutoryTableService { boolean isCurrent(String tableId); }
    interface HolidayCalendarService { boolean isCurrent(String calendarId); }

    static class ClaimInitiationTransformationService {
        private final ChannelConfigService channelConfigService;
        private final StatutoryTableService statutoryTableService;
        private final HolidayCalendarService holidayCalendarService;

        ClaimInitiationTransformationService(ChannelConfigService channelConfigService,
                                             StatutoryTableService statutoryTableService,
                                             HolidayCalendarService holidayCalendarService) {
            this.channelConfigService = channelConfigService;
            this.statutoryTableService = statutoryTableService;
            this.holidayCalendarService = holidayCalendarService;
        }

        void validateAndTransform(Map<String, Object> payload) {
            String[] required = {"fnol_submission_timestamp", "acknowledgment_timestamp", "channel_type", "statutory_deadline_hours"};
            for (String key : required) {
                if (!payload.containsKey(key) || payload.get(key) == null) {
                    throw new ValidationException("Missing required field: " + key);
                }
            }

            String fnolTs = (String) payload.get("fnol_submission_timestamp");
            String ackTs = (String) payload.get("acknowledgment_timestamp");
            validateIso8601(fnolTs, "fnol_submission_timestamp");
            validateIso8601(ackTs, "acknowledgment_timestamp");

            String channel = (String) payload.get("channel_type");
            Integer deadline = (Integer) payload.get("statutory_deadline_hours");
            if (channelConfigService.getDeadlineHours(channel) == null || deadline == null) {
                throw new ValidationException("Deadline must be configured per channel");
            }

            if (payload.containsKey("holiday_calendar")) {
                String calId = (String) payload.get("holiday_calendar");
                if (!holidayCalendarService.isCurrent(calId)) {
                    throw new ValidationException("Holiday calendar must be current");
                }
            }
            if (payload.containsKey("statutory_deadline_hours")) {
                if (!statutoryTableService.isCurrent("STATUTORY_TABLE_V1")) {
                    throw new ValidationException("Statutory tables must be current");
                }
            }
        }

        private void validateIso8601(String value, String fieldName) {
            try {
                Instant.parse(value);
            } catch (DateTimeParseException e) {
                throw new ValidationException(fieldName + " must be ISO-8601");
            }
        }
    }
}
