package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class AuditReportsGeneratedAccuratelyTest {

    @Mock
    private DecisionStateTransitionService stateTransitionService;

    @Mock
    private AuditReportService auditReportService;

    private InsuredEngagementProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new InsuredEngagementProcessor(stateTransitionService, auditReportService);
    }

    @Test
    void audit_reports_generated_accurately() {
        // Given
        String insuredId = "INS-10293";
        String fromState = "ENROLLED";
        String toState = "CLAIM_SUBMITTED";
        TransitionContext context = new TransitionContext(insuredId, fromState, toState, Instant.now());

        when(stateTransitionService.evaluateAndTransition(context)).thenReturn(true);

        // When
        boolean transitionResult = processor.processEngagementDecision(context);

        // Then
        assertTrue(transitionResult, "State transition should succeed");
        verify(auditReportService, times(1)).generateAuditReport(eq(insuredId), eq(toState), any());

        ArgumentCaptor<Map<String, Object>> reportPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditReportService).generateAuditReport(eq(insuredId), eq(toState), reportPayloadCaptor.capture());
        Map<String, Object> generatedReport = reportPayloadCaptor.getValue();

        assertEquals(insuredId, generatedReport.get("insuredId"));
        assertEquals(toState, generatedReport.get("currentState"));
        assertEquals(fromState, generatedReport.get("previousState"));
        assertNotNull(generatedReport.get("timestamp"));
        assertEquals("ACCURATE", generatedReport.get("validationStatus"));
    }
}
