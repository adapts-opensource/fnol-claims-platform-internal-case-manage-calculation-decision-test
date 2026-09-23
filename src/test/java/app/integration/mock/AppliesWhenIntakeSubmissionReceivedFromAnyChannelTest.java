package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalCaseManagementDecisionCalculationMockTest {

    @Mock
    private IntakeSubmissionGateway intakeSubmissionGateway;

    @Mock
    private DecisionCalculationService decisionCalculationService;

    @Mock
    private CaseManagementStateTracker stateTracker;

    private InternalCaseManagementDecisionProcessor decisionProcessor;

    @BeforeEach
    void setUp() {
        decisionProcessor = new InternalCaseManagementDecisionProcessor(
                intakeSubmissionGateway,
                decisionCalculationService,
                stateTracker
        );
    }

    @Test
    void applies_when_intake_submission_received_from_any_channel() {
        // Given
        String submissionId = UUID.randomUUID().toString();
        Map<String, Object> intakePayload = Map.of(
                "claimId", "CLAIM-001",
                "channel", "WEB",
                "riskScore", 75.0,
                "timestamp", System.currentTimeMillis()
        );

        when(intakeSubmissionGateway.receiveSubmission(submissionId, intakePayload)).thenReturn(true);

        // When
        decisionProcessor.processIntakeSubmission(submissionId, intakePayload);

        // Then
        ArgumentCaptor<Map<String, Object>> capturedPayload = ArgumentCaptor.forClass(Map.class);
        verify(decisionCalculationService).calculateDecision(capturedPayload.capture());

        Map<String, Object> calculatedPayload = capturedPayload.getValue();
        assertEquals("CLAIM-001", calculatedPayload.get("claimId"));
        assertEquals("WEB", calculatedPayload.get("channel"));
        assertEquals(75.0, calculatedPayload.get("riskScore"));

        verify(stateTracker).updateCaseState(submissionId, "DECISION_CALCULATION_INITIATED");
        verifyNoMoreInteractions(intakeSubmissionGateway, decisionCalculationService, stateTracker);
    }

    // Minimal domain interfaces & processor to ensure test compiles in isolation
    interface IntakeSubmissionGateway {
        boolean receiveSubmission(String id, Map<String, Object> payload);
    }

    interface DecisionCalculationService {
        void calculateDecision(Map<String, Object> payload);
    }

    interface CaseManagementStateTracker {
        void updateCaseState(String id, String state);
    }

    static class InternalCaseManagementDecisionProcessor {
        private final IntakeSubmissionGateway gateway;
        private final DecisionCalculationService service;
        private final CaseManagementStateTracker tracker;

        InternalCaseManagementDecisionProcessor(IntakeSubmissionGateway g, DecisionCalculationService s, CaseManagementStateTracker t) {
            this.gateway = g;
            this.service = s;
            this.tracker = t;
        }

        void processIntakeSubmission(String id, Map<String, Object> payload) {
            gateway.receiveSubmission(id, payload);
            service.calculateDecision(payload);
            tracker.updateCaseState(id, "DECISION_CALCULATION_INITIATED");
        }
    }
}
