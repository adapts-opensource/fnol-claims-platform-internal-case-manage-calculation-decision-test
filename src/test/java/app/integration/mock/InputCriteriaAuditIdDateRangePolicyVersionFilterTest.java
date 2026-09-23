package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InputCriteriaAuditIdDateRangePolicyVersionFilterTest {

    @Mock
    private StateTransitionQueryHandler stateTransitionQueryHandler;

    @InjectMocks
    private InsuredEngagementTrackingService insuredEngagementTrackingService;

    private static final String AUDIT_ID = "audit-req-001";
    private static final LocalDate DATE_FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2024, 12, 31);
    private static final String POLICY_VERSION = "v3.2";

    @BeforeEach
    void setUp() {
        // Ensure clean mock state for each execution
    }

    @Test
    void input_criteria_audit_id_date_range_policy_version_filter() {
        // Arrange
        List<StateTransitionEvent> expectedEvents = Collections.singletonList(
            new StateTransitionEvent("evt-99", AUDIT_ID, "DRAFT", "REVIEWED", DATE_FROM)
        );

        when(stateTransitionQueryHandler.fetchByAuditAndDateAndVersion(
                eq(AUDIT_ID),
                eq(DATE_FROM),
                eq(DATE_TO),
                eq(POLICY_VERSION)
            )
        ).thenReturn(expectedEvents);

        // Act
        List<StateTransitionEvent> actualEvents = insuredEngagementTrackingService.trackStateTransitions(
                AUDIT_ID, DATE_FROM, DATE_TO, POLICY_VERSION
            );

        // Assert
        assertNotNull(actualEvents, "Result list should not be null");
        assertEquals(1, actualEvents.size(), "Should return exactly one event");
        assertEquals("evt-99", actualEvents.get(0).eventId(), "Event ID should match");
        assertEquals("REVIEWED", actualEvents.get(0).newState(), "New state should match");

        verify(stateTransitionQueryHandler, times(1)).fetchByAuditAndDateAndVersion(
                eq(AUDIT_ID),
                eq(DATE_FROM),
                eq(DATE_TO),
                eq(POLICY_VERSION)
            );
    }

    // Minimal static record to avoid external dependencies in this mock file
    private record StateTransitionEvent(String eventId, String auditId, String oldState, String newState, LocalDate occurredOn) {}
}
