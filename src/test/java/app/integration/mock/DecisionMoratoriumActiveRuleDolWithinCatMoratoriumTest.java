package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class ClaimInitiationRoutingDecisionMockTest {

    private MockRedisService redisService;
    private MockDynamoDbService dynamoDbService;
    private MockSesService sesService;
    private DecisionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        redisService = mock(MockRedisService.class);
        dynamoDbService = mock(MockDynamoDbService.class);
        sesService = mock(MockSesService.class);
        orchestrator = new DecisionOrchestrator(redisService, dynamoDbService, sesService);
    }

    @Test
    void decision_moratorium_active_rule_dol_within_cat_moratorium_expected_outcome_flag_restriction_notify_compliance_hold_if_required() {
        // Given: Payload simulating DOL within category moratorium
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "claim-123");
        payload.put("decision", "Moratorium Active");
        payload.put("rule", "DOL within cat moratorium");
        payload.put("dateOfLoss", "2023-11-15");
        payload.put("policyCategory", "auto");

        // Mock Redis cache check for moratorium status
        when(redisService.getCacheValue("Cache & Reference Data:cache:moratorium_auto"))
                .thenReturn("active");

        // Mock DynamoDB policy lookup
        when(dynamoDbService.getItemPayload("Claims & Policy Data Store_table", "pk:claim-123"))
                .thenReturn(Map.of("category", "auto", "status", "new"));

        // Mock SES compliance notification
        when(sesService.sendEmail("compliance@newco-insurance.com",
                List.of("compliance@newco-insurance.com"), "us-east-1"))
                .thenReturn("msg-id-mock-001");

        // When: Execute decision orchestration
        Map<String, Object> result = orchestrator.evaluate(payload);

        // Then: Verify expected outcomes
        assertNotNull(result);
        assertEquals("Moratorium Active", result.get("decision"));
        assertTrue((Boolean) result.getOrDefault("flag_restriction", Boolean.FALSE));
        assertTrue((Boolean) result.getOrDefault("notify_compliance", Boolean.FALSE));
        assertTrue((Boolean) result.getOrDefault("hold_if_required", Boolean.FALSE));

        // Verify external I/O interactions
        verify(redisService, times(1)).getCacheValue("Cache & Reference Data:cache:moratorium_auto");
        verify(dynamoDbService, times(1)).getItemPayload("Claims & Policy Data Store_table", "pk:claim-123");
        verify(sesService, times(1)).sendEmail("compliance@newco-insurance.com",
                List.of("compliance@newco-insurance.com"), "us-east-1");
    }

    // Mock Service Interfaces for isolated testing
    interface MockRedisService {
        String getCacheValue(String key);
    }

    interface MockDynamoDbService {
        Map<String, Object> getItemPayload(String tableName, String partitionKey);
    }

    interface MockSesService {
        String sendEmail(String from, List<String> to, String region);
    }

    // Orchestrator under test
    static class DecisionOrchestrator {
        private final MockRedisService redis;
        private final MockDynamoDbService dynamo;
        private final MockSesService ses;

        DecisionOrchestrator(MockRedisService redis, MockDynamoDbService dynamo, MockSesService ses) {
            this.redis = redis;
            this.dynamo = dynamo;
            this.ses = ses;
        }

        Map<String, Object> evaluate(Map<String, Object> payload) {
            Map<String, Object> outcome = new HashMap<>();
            outcome.put("decision", payload.get("decision"));
            outcome.put("rule", payload.get("rule"));

            if ("Moratorium Active".equals(payload.get("decision")) &&
                    "DOL within cat moratorium".equals(payload.get("rule"))) {
                outcome.put("flag_restriction", true);
                outcome.put("notify_compliance", true);
                outcome.put("hold_if_required", true);
                ses.sendEmail("compliance@newco-insurance.com",
                        List.of("compliance@newco-insurance.com"), "us-east-1");
            }
            return outcome;
        }
    }
}
