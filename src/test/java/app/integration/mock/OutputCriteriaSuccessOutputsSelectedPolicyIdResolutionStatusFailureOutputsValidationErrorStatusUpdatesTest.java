package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ClaimInitiationRoutingDecisionMockTest {

    @Mock
    private DecisionOrchestrationService decisionOrchestrationService;
    @Mock
    private CacheClient cacheClient;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private StatusUpdater statusUpdater;
    @Mock
    private CommunicationService communicationService;

    private ClaimInitiationRoutingDecisionHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new ClaimInitiationRoutingDecisionHandler(
                decisionOrchestrationService, cacheClient, dynamoDbClient,
                eventPublisher, statusUpdater, communicationService
        );
    }

    @Test
    void output_criteria_success_outputs_selected_policy_id_resolution_status_failure_outputs_validation_error_status_updates_policy_resolved_emitted_events_policy_resolved_user_visible_outputs_fnol_status_updated() {
        // Arrange
        String claimId = "claim-init-001";
        Map<String, Object> inputPayload = Map.of(
                "id", claimId,
                "policy_number", "POL-998877",
                "claimant_email", "insured@newco.com",
                "status", "INITIATED"
        );

        // Mock Redis cache for decision criteria
        String cacheConfig = """
            {
              "success_outputs": ["selected_policy_id", "resolution_status"],
              "failure_outputs": ["validation_error"],
              "status_updates": ["POLICY_RESOLVED"],
              "emitted_events": ["policy.resolved"],
              "user_visible_outputs": ["FNOL status updated."]
            }
            """;
        when(cacheClient.get(eq("Cache & Reference Data:cache:decision_criteria")))
                .thenReturn(cacheConfig);

        // Mock DynamoDB policy lookup
        Map<String, Object> policyRecord = Map.of("pk", "POL-998877", "status", "ACTIVE", "routing_rule", "STANDARD");
        when(dynamoDbClient.getItem(eq("Claims & Policy Data Store_table"), any(Map.class)))
                .thenReturn(Map.of("Item", policyRecord));

        // Mock decision evaluation result
        DecisionResult decisionResult = new DecisionResult(
                true, "POL-998877", "ROUTED", null,
                List.of("POLICY_RESOLVED"), List.of("policy.resolved"), "FNOL status updated."
        );
        when(decisionOrchestrationService.evaluateDecision(any(Map.class), anyString()))
                .thenReturn(decisionResult);

        // Act
        DecisionOutcome outcome = handler.executeOrchestration(inputPayload);

        // Assert - Success outputs
        assertNotNull(outcome);
        assertEquals("POL-998877", outcome.getSelectedPolicyId());
        assertEquals("ROUTED", outcome.getResolutionStatus());
        assertNull(outcome.getValidationError());

        // Assert - Status updates
        verify(statusUpdater, times(1)).updateStatus(eq(claimId), eq("POLICY_RESOLVED"));

        // Assert - Emitted events
        verify(eventPublisher, times(1)).publish(eq("policy.resolved"), any(EventPayload.class));

        // Assert - User visible outputs / Communication
        verify(communicationService, times(1)).sendNotification(
                eq("newco-insurance@newco.com"),
                eq(List.of("insured@newco.com")),
                eq("FNOL status updated."),
                eq("us-east-1")
        );
    }

    // --- Stubbed Interfaces & Classes for Compilation ---
    interface DecisionOrchestrationService {
        DecisionResult evaluateDecision(Map<String, Object> payload, String criteriaKey);
    }

    interface CacheClient {
        String get(String key);
    }

    interface DynamoDbClient {
        Map<String, Object> getItem(String tableName, Map<String, Object> key);
    }

    interface EventPublisher {
        void publish(String eventType, EventPayload payload);
    }

    interface StatusUpdater {
        void updateStatus(String entityId, String newStatus);
    }

    interface CommunicationService {
        void sendNotification(String from, List<String> to, String body, String region);
    }

    record DecisionResult(boolean success, String selectedPolicyId, String resolutionStatus,
                          String validationError, List<String> statusUpdates, List<String> emittedEvents, String userVisibleOutput) {}

    record EventPayload(String claimId, String eventType, Map<String, Object> data) {}

    record DecisionOutcome(String selectedPolicyId, String resolutionStatus, String validationError, String userVisibleOutput) {
        public static DecisionOutcome from(DecisionResult r, String visible) {
            return new DecisionOutcome(r.selectedPolicyId(), r.resolutionStatus(), r.validationError(), visible);
        }
    }

    class ClaimInitiationRoutingDecisionHandler {
        private final DecisionOrchestrationService decisionOrchestrationService;
        private final CacheClient cacheClient;
        private final DynamoDbClient dynamoDbClient;
        private final EventPublisher eventPublisher;
        private final StatusUpdater statusUpdater;
        private final CommunicationService communicationService;

        ClaimInitiationRoutingDecisionHandler(DecisionOrchestrationService decisionOrchestrationService,
                                              CacheClient cacheClient,
                                              DynamoDbClient dynamoDbClient,
                                              EventPublisher eventPublisher,
                                              StatusUpdater statusUpdater,
                                              CommunicationService communicationService) {
            this.decisionOrchestrationService = decisionOrchestrationService;
            this.cacheClient = cacheClient;
            this.dynamoDbClient = dynamoDbClient;
            this.eventPublisher = eventPublisher;
            this.statusUpdater = statusUpdater;
            this.communicationService = communicationService;
        }

        DecisionOutcome executeOrchestration(Map<String, Object> payload) {
            String claimId = (String) payload.get("id");
            cacheClient.get("Cache & Reference Data:cache:decision_criteria");
            DecisionResult result = decisionOrchestrationService.evaluateDecision(payload, "decision_criteria");
            if (result.success()) {
                statusUpdater.updateStatus(claimId, result.statusUpdates().get(0));
                eventPublisher.publish(result.emittedEvents().get(0), new EventPayload(claimId, result.emittedEvents().get(0), payload));
                String email = (String) payload.get("claimant_email");
                communicationService.sendNotification("newco-insurance@newco.com", List.of(email), result.userVisibleOutput(), "us-east-1");
                return DecisionOutcome.from(result, result.userVisibleOutput());
            }
            throw new RuntimeException("Decision failed");
        }
    }
}
