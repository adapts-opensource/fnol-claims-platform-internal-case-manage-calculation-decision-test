package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.List;
import java.util.UUID;

// JUnit 5 test class with @Test methods
public class DecisionTemplateSelectionRuleChannelClaimTypeMatchTest {

    private MockRedisClient redisClient;
    private MockDynamoDbClient dynamoDbClient;
    private MockSesClient sesClient;
    private DecisionCalculationService decisionCalculationService;

    @BeforeEach
    void setUp() {
        redisClient = mock(MockRedisClient.class);
        dynamoDbClient = mock(MockDynamoDbClient.class);
        sesClient = mock(MockSesClient.class);
        decisionCalculationService = new DecisionCalculationService(redisClient, dynamoDbClient, sesClient);
    }

    @Test
    void decision_template_selection_rule_channel_claim_type_match_expected_outcome_correct_notice_dispatched() {
        // Arrange: Construct claim initiation payload matching channel + claim type rule
        String claimId = UUID.randomUUID().toString();
        String channel = "WEB_PORTAL";
        String claimType = "AUTO_COLLISION";
        String expectedTemplate = "TEMPLATE_AUTO_COLLISION_WEB_NOTICE";

        Map<String, Object> claimPayload = Map.of(
                "id", claimId,
                "channel", channel,
                "claimType", claimType,
                "payload", Map.of("incidentDate", "2024-01-15", "description", "Fender bender")
        );

        // Mock Redis cache lookup for template mapping (Cache & Reference Data_elasticache)
        String cacheKey = "Cache & Reference Data:cache:" + channel + ":" + claimType;
        when(redisClient.get(cacheKey)).thenReturn(expectedTemplate);

        // Mock DynamoDB validation lookup (Claims & Policy Data Store_dynamodb)
        when(dynamoDbClient.getItem("Claims & Policy Data Store_table", "pk", claimId))
                .thenReturn(Map.of("status", "INITIATED", "policyId", "POL-999"));

        // Act: Trigger decision calculation & notice routing
        Map<String, Object> result = decisionCalculationService.calculateRoutingDecision(claimPayload);

        // Assert: Verify correct template selection and routing action
        assertEquals(expectedTemplate, result.get("selectedTemplate"));
        assertEquals("ROUTE_TO_ADJUSTER", result.get("routingAction"));
        assertNotNull(result.get("traceId"), "NFR: Observability - traceId required for structured logging");

        // Assert: Verify correct notice dispatched via SES (Communication & Acknowledgment Service_ses)
        verify(sesClient, times(1)).sendNotice(
                eq("claims-notice@newco-insurance.com"),
                anyList(),
                eq("us-east-1"),
                eq(expectedTemplate),
                eq(claimId)
        );

        // NFR: Security & Compliance - Ensure no PII leaked in mock assertions
        assertFalse(result.toString().contains("PII"), "NFR: Compliance - PII must not be logged or returned in decision payload");
    }

    // Mock interfaces for infra I/O contracts
    interface MockRedisClient {
        String get(String key);
    }

    interface MockDynamoDbClient {
        Map<String, Object> getItem(String tableName, String partitionKey, String keyValue);
    }

    interface MockSesClient {
        void sendNotice(String fromAddress, List<String> toAddresses, String region, String templateId, String claimId);
    }

    // Simplified service under test (isolated for unit/mock testing)
    static class DecisionCalculationService {
        private final MockRedisClient redisClient;
        private final MockDynamoDbClient dynamoDbClient;
        private final MockSesClient sesClient;

        DecisionCalculationService(MockRedisClient redisClient, MockDynamoDbClient dynamoDbClient, MockSesClient sesClient) {
            this.redisClient = redisClient;
            this.dynamoDbClient = dynamoDbClient;
            this.sesClient = sesClient;
        }

        Map<String, Object> calculateRoutingDecision(Map<String, Object> payload) {
            String claimId = (String) payload.get("id");
            String channel = (String) payload.get("channel");
            String claimType = (String) payload.get("claimType");

            // Input validation NFR
            if (claimId == null || channel == null || claimType == null) {
                throw new IllegalArgumentException("Missing required claim initiation fields");
            }

            String cacheKey = "Cache & Reference Data:cache:" + channel + ":" + claimType;
            String templateId = redisClient.get(cacheKey);

            Map<String, Object> dbItem = dynamoDbClient.getItem("Claims & Policy Data Store_table", "pk", claimId);
            if (dbItem == null || !"INITIATED".equals(dbItem.get("status"))) {
                throw new IllegalArgumentException("Invalid claim state for routing");
            }

            Map<String, Object> result = Map.of(
                    "selectedTemplate", templateId,
                    "routingAction", "ROUTE_TO_ADJUSTER",
                    "traceId", UUID.randomUUID().toString()
            );

            // Dispatch notice via mocked SES
            sesClient.sendNotice("claims-notice@newco-insurance.com", List.of("claims-admin@newco.com"), "us-east-1", templateId, claimId);
            return result;
        }
    }
}
