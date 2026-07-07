package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ReporterChangedMidProcessTest {

    @Mock
    private DataPersistenceService dataPersistenceService;

    @Mock
    private CommunicationService communicationService;

    @Mock
    private OrchestrationDecisionService orchestrationDecisionService;

    @Test
    void reporter_changed_mid_process() {
        // Given: Initial claim state with original reporter
        String claimId = "CLM-10045";
        String incidentId = "INC-7892";
        String originalReporterId = "USR-REP-OLD";
        String newReporterId = "USR-REP-NEW";

        Map<String, Object> initialState = Map.of(
            "claim_id", claimId,
            "incident_id", incidentId,
            "reporter_id", originalReporterId,
            "status", "IN_PROGRESS",
            "version", 1
        );

        when(dataPersistenceService.getState(claimId)).thenReturn(initialState);
        when(orchestrationDecisionService.evaluate(any(Map.class))).thenReturn(
            Map.of("decision", "APPROVED", "next_step", "PERSIST_AND_NOTIFY")
        );

        // When: Reporter is changed mid-process during orchestration
        Map<String, Object> result = orchestrationDecisionService.executeDecision(
            claimId, "REPORTER_CHANGED", Map.of("new_reporter_id", newReporterId)
        );

        // Then: Verify decision outcome
        assertNotNull(result);
        assertEquals("APPROVED", result.get("decision"));
        assertEquals("PERSIST_AND_NOTIFY", result.get("next_step"));

        // Verify DynamoDB persistence mock
        verify(dataPersistenceService, times(1)).updateState(eq(claimId), any(Map.class));
        ArgumentCaptor<Map> stateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dataPersistenceService).updateState(eq(claimId), stateCaptor.capture());
        Map<String, Object> capturedState = stateCaptor.getValue();
        assertEquals(newReporterId, capturedState.get("reporter_id"));
        assertEquals(2, capturedState.get("version"));

        // Verify SES notification mock
        verify(communicationService, times(1)).sendEmail(
            eq("claims-ops@newco-insurance.com"),
            argThat(toList -> toList.contains(originalReporterId + "@newco-insurance.com") 
                            && toList.contains(newReporterId + "@newco-insurance.com")),
            eq("Insured Engagement: Reporter Updated for " + claimId),
            eq("us-east-1")
        );
    }

    // Mock interfaces representing external I/O contracts
    static interface DataPersistenceService {
        Map<String, Object> getState(String claimId);
        void updateState(String claimId, Map<String, Object> state);
    }

    static interface CommunicationService {
        String sendEmail(String from, List<String> to, String subject, String region);
    }

    static interface OrchestrationDecisionService {
        Map<String, Object> evaluate(Map<String, Object> context);
        Map<String, Object> executeDecision(String claimId, String eventType, Map<String, Object> payload);
    }
}
