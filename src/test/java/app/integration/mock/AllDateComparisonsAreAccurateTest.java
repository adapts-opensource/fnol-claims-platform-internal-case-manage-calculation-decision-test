package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionTransformationTest {

    @Mock
    private ClaimDecisionTransformationService transformationService;

    @BeforeEach
    void setUp() {
        // Mock external transformation I/O to simulate accurate date comparison logic
        when(transformationService.transformDecision(any(Map.class)))
                .thenAnswer(invocation -> {
                    Map<String, Object> payload = invocation.getArgument(0);
                    String incidentDateStr = (String) payload.get("incident_date");
                    String decisionDateStr = (String) payload.get("decision_date");

                    LocalDate incidentDate = LocalDate.parse(incidentDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                    LocalDate decisionDate = LocalDate.parse(decisionDateStr, DateTimeFormatter.ISO_LOCAL_DATE);

                    // Simulate precise date comparison operations
                    boolean isBefore = incidentDate.isBefore(decisionDate);
                    boolean isAfter = incidentDate.isAfter(decisionDate);
                    boolean isEqual = incidentDate.isEqual(decisionDate);

                    payload.put("is_incident_before_decision", isBefore);
                    payload.put("is_incident_after_decision", isAfter);
                    payload.put("is_decision_same_day", isEqual);
                    return payload;
                });
    }

    @Test
    void all_date_comparisons_are_accurate() {
        // Arrange: Decision occurs after incident
        Map<String, Object> payloadFuture = Map.of(
                "incident_date", "2023-05-10",
                "decision_date", "2023-05-15"
        );

        // Arrange: Decision occurs before incident
        Map<String, Object> payloadPast = Map.of(
                "incident_date", "2023-06-01",
                "decision_date", "2023-05-20"
        );

        // Arrange: Decision occurs on the same day
        Map<String, Object> payloadSameDay = Map.of(
                "incident_date", "2023-07-04",
                "decision_date", "2023-07-04"
        );

        // Act: Execute transformation for all date scenarios
        Map<String, Object> resultFuture = transformationService.transformDecision(payloadFuture);
        Map<String, Object> resultPast = transformationService.transformDecision(payloadPast);
        Map<String, Object> resultSameDay = transformationService.transformDecision(payloadSameDay);

        // Assert: Verify accuracy of all date comparisons
        assertTrue((Boolean) resultFuture.get("is_incident_before_decision"));
        assertFalse((Boolean) resultFuture.get("is_incident_after_decision"));
        assertFalse((Boolean) resultFuture.get("is_decision_same_day"));

        assertFalse((Boolean) resultPast.get("is_incident_before_decision"));
        assertTrue((Boolean) resultPast.get("is_incident_after_decision"));
        assertFalse((Boolean) resultPast.get("is_decision_same_day"));

        assertFalse((Boolean) resultSameDay.get("is_incident_before_decision"));
        assertFalse((Boolean) resultSameDay.get("is_incident_after_decision"));
        assertTrue((Boolean) resultSameDay.get("is_decision_same_day"));
    }
}
