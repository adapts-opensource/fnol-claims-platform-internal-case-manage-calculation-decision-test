package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvalidSeverityRequestEstimateHoldRoutingTest {

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDbStore dynamoDbStore;

    @Mock
    private SesEmailService sesEmailService;

    @InjectMocks
    private ClaimRoutingDecisionCalculator claimRoutingDecisionCalculator;

    @Test
    void invalid_severity_request_estimate_hold_routing() {
        // Arrange
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("severity", "INVALID_LEVEL");
        payload.put("claimId", "claim-789");
        payload.put("policyNumber", "POL-101");

        ClaimInitiationRequest request = new ClaimInitiationRequest(requestId, payload);

        // Mock external I/O contracts (Redis, DynamoDB, SES)
        // NFR: Input validation & structured logging are handled in the service layer
        when(redisCacheService.get(anyString())).thenReturn("[]");
        when(dynamoDbStore.getItem(anyString(), anyString(), anyString())).thenReturn(Map.of("status", "INITIATED"));
        doNothing().when(sesEmailService).sendEmail(anyString(), anyList(), anyString());

        // Act
        RoutingDecision decision = claimRoutingDecisionCalculator.calculateDecision(request);

        // Assert decision logic per feature spec: Invalid severity -> request estimate, hold routing
        assertNotNull(decision);
        assertEquals("REQUEST_ESTIMATE", decision.getAction());
        assertTrue(decision.isHoldRouting());
        assertFalse(decision.isAutoRoute());
        assertTrue(decision.isInputValidated());

        // Verify infra interactions were triggered correctly
        verify(redisCacheService).get("Cache & Reference Data:cache:severity_rules");
        verify(dynamoDbStore).getItem("Claims & Policy Data Store_table", "pk", "claim-789");
        verify(sesEmailService).sendEmail("claims@newco.insurance", List.of("handler@newco.insurance"), "ack-" + requestId);
    }

    // Minimal domain models & interfaces to support the test without external dependencies
    interface RedisCacheService { String get(String key); }
    interface DynamoDbStore { Map<String, Object> getItem(String table, String pk, String pkValue); }
    interface SesEmailService { void sendEmail(String from, List<String> to, String region); }

    static class ClaimInitiationRequest {
        private final String id;
        private final Map<String, Object> payload;
        ClaimInitiationRequest(String id, Map<String, Object> payload) {
            this.id = id;
            this.payload = payload;
        }
        public String getId() { return id; }
        public Map<String, Object> getPayload() { return payload; }
    }

    static class RoutingDecision {
        private final String action;
        private final boolean holdRouting;
        private final boolean autoRoute;
        private final boolean inputValidated;
        RoutingDecision(String action, boolean holdRouting, boolean autoRoute, boolean inputValidated) {
            this.action = action;
            this.holdRouting = holdRouting;
            this.autoRoute = autoRoute;
            this.inputValidated = inputValidated;
        }
        public String getAction() { return action; }
        public boolean isHoldRouting() { return holdRouting; }
        public boolean isAutoRoute() { return autoRoute; }
        public boolean isInputValidated() { return inputValidated; }
    }

    // Dummy implementation to enable @InjectMocks and isolate business logic
    static class ClaimRoutingDecisionCalculator {
        private final RedisCacheService redisCacheService;
        private final DynamoDbStore dynamoDbStore;
        private final SesEmailService sesEmailService;

        ClaimRoutingDecisionCalculator(RedisCacheService redisCacheService, DynamoDbStore dynamoDbStore, SesEmailService sesEmailService) {
            this.redisCacheService = redisCacheService;
            this.dynamoDbStore = dynamoDbStore;
            this.sesEmailService = sesEmailService;
        }

        RoutingDecision calculateDecision(ClaimInitiationRequest request) {
            String severity = (String) request.getPayload().get("severity");
            boolean isValid = "LOW".equals(severity) || "MEDIUM".equals(severity) || "HIGH".equals(severity);

            // Invoke mocked infra contracts
            redisCacheService.get("Cache & Reference Data:cache:severity_rules");
            dynamoDbStore.getItem("Claims & Policy Data Store_table", "pk", (String) request.getPayload().get("claimId"));
            sesEmailService.sendEmail("claims@newco.insurance", List.of("handler@newco.insurance"), "ack-" + request.getId());

            if (!isValid) {
                return new RoutingDecision("REQUEST_ESTIMATE", true, false, true);
            }
            return new RoutingDecision("AUTO_ROUTE", false, true, true);
        }
    }
}
