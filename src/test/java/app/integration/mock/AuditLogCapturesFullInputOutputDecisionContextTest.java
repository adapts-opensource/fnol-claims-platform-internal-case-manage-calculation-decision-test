package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class AuditLogCapturesFullInputOutputDecisionContextTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private DecisionTransformationService decisionTransformationService;

    private InsuredEngagementService insuredEngagementService;

    @Captor
    private ArgumentCaptor<AuditEvent> auditEventCaptor;

    @BeforeEach
    void setUp() {
        insuredEngagementService = new InsuredEngagementService(decisionTransformationService, auditLogRepository);
    }

    @Test
    void audit_log_captures_full_input_output_decision_context() {
        // Arrange
        String insuredId = "INS-88492";
        String policyNumber = "POL-99210";
        String decisionId = "DEC-77341";
        String decisionType = "RESERVE_APPROVAL";

        Map<String, Object> inputPayload = Map.of(
            "insuredId", insuredId,
            "policyNumber", policyNumber,
            "requestedAmount", 15000.00,
            "currency", "USD"
        );

        DecisionContext context = new DecisionContext(
            decisionId,
            decisionType,
            Instant.parse("2023-10-27T10:15:30Z"),
            "SYSTEM_AUTO"
        );

        Map<String, Object> outputPayload = Map.of(
            "decisionId", decisionId,
            "status", "APPROVED",
            "approvedAmount", 15000.00,
            "approvalTimestamp", Instant.parse("2023-10-27T10:15:31Z")
        );

        when(decisionTransformationService.transform(inputPayload, context))
            .thenReturn(outputPayload);

        // Act
        insuredEngagementService.processDecision(inputPayload, context);

        // Assert
        verify(auditLogRepository, times(1)).save(auditEventCaptor.capture());
        AuditEvent capturedEvent = auditEventCaptor.getValue();

        assertEquals(AuditEvent.ActionType.DECISION_TRANSFORMATION, capturedEvent.getActionType());
        assertEquals(insuredId, capturedEvent.getInsuredId());
        assertEquals(inputPayload, capturedEvent.getInput());
        assertEquals(outputPayload, capturedEvent.getOutput());
        assertEquals(context, capturedEvent.getContext());
    }

    // --- Inner Stubs for Compilation ---

    static class InsuredEngagementService {
        private final DecisionTransformationService decisionTransformationService;
        private final AuditLogRepository auditLogRepository;

        InsuredEngagementService(DecisionTransformationService decisionTransformationService, AuditLogRepository auditLogRepository) {
            this.decisionTransformationService = decisionTransformationService;
            this.auditLogRepository = auditLogRepository;
        }

        void processDecision(Map<String, Object> input, DecisionContext context) {
            Map<String, Object> output = decisionTransformationService.transform(input, context);
            AuditEvent event = new AuditEvent(
                AuditEvent.ActionType.DECISION_TRANSFORMATION,
                context.getInsuredIdFromContextOrInput(input),
                input,
                output,
                context
            );
            auditLogRepository.save(event);
        }
    }

    interface DecisionTransformationService {
        Map<String, Object> transform(Map<String, Object> input, DecisionContext context);
    }

    interface AuditLogRepository {
        void save(AuditEvent event);
    }

    static class DecisionContext {
        private final String decisionId;
        private final String decisionType;
        private final Instant timestamp;
        private final String initiator;

        DecisionContext(String decisionId, String decisionType, Instant timestamp, String initiator) {
            this.decisionId = decisionId;
            this.decisionType = decisionType;
            this.timestamp = timestamp;
            this.initiator = initiator;
        }

        public String getDecisionId() { return decisionId; }
        public String getDecisionType() { return decisionType; }
        public Instant getTimestamp() { return timestamp; }
        public String getInitiator() { return initiator; }
        public String getInsuredIdFromContextOrInput(Map<String, Object> input) {
            return (String) input.get("insuredId");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DecisionContext)) return false;
            DecisionContext that = (DecisionContext) o;
            return decisionId.equals(that.decisionId) &&
                   decisionType.equals(that.decisionType) &&
                   timestamp.equals(that.timestamp) &&
                   initiator.equals(that.initiator);
        }
    }

    static class AuditEvent {
        public enum ActionType { DECISION_TRANSFORMATION }

        private final ActionType actionType;
        private final String insuredId;
        private final Map<String, Object> input;
        private final Map<String, Object> output;
        private final DecisionContext context;

        AuditEvent(ActionType actionType, String insuredId, Map<String, Object> input, Map<String, Object> output, DecisionContext context) {
            this.actionType = actionType;
            this.insuredId = insuredId;
            this.input = input;
            this.output = output;
            this.context = context;
        }

        public ActionType getActionType() { return actionType; }
        public String getInsuredId() { return insuredId; }
        public Map<String, Object> getInput() { return input; }
        public Map<String, Object> getOutput() { return output; }
        public DecisionContext getContext() { return context; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AuditEvent)) return false;
            AuditEvent that = (AuditEvent) o;
            return actionType == that.actionType &&
                   insuredId.equals(that.insuredId) &&
                   input.equals(that.input) &&
                   output.equals(that.output) &&
                   context.equals(that.context);
        }
    }
}
