package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationTest {

    private static final Logger log = LoggerFactory.getLogger(ClaimInitiationRoutingDecisionCalculationTest.class);

    @Mock
    private RedisCache redisCache;

    @Mock
    private DynamoDBTable dynamoDBTable;

    @Mock
    private SesEmailService sesEmailService;

    @Mock
    private DecisionRuleEngine ruleEngine;

    private ClaimDecisionCalculationService service;

    @BeforeEach
    void setUp() {
        service = new ClaimDecisionCalculationService(ruleEngine, redisCache, dynamoDBTable, sesEmailService, log);
    }

    @Test
    void decision_version_alignment_rule_rule_version_matches_execution_time_expected_outcome_compliance_verified_or_discrepancy_logged() {
        // Arrange
        String claimId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "ruleVersion", "1.0.0",
            "executionTimestamp", System.currentTimeMillis(),
            "expectedRuleVersion", "1.0.0"
        );

        // Mock infra I/O contracts (Redis, DynamoDB, SES)
        when(redisCache.get("Cache & Reference Data:cache:" + claimId)).thenReturn("cached_rule_config");
        when(dynamoDBTable.getItem("Claims & Policy Data Store_table", claimId)).thenReturn(Map.of("status", "INITIATED"));
        when(ruleEngine.evaluateVersionAlignment(payload)).thenReturn(true);

        // Act
        boolean isCompliant = service.processDecisionCalculation(payload);

        // Assert
        assertTrue(isCompliant, "Expected compliance verified when rule version matches execution time");
        verify(ruleEngine).evaluateVersionAlignment(payload);
        verify(redisCache).get("Cache & Reference Data:cache:" + claimId);
        verify(dynamoDBTable).getItem("Claims & Policy Data Store_table", claimId);
        // Verify discrepancy logging is not triggered in the happy path
        verifyNoInteractions(log);
    }

    // Mock interfaces representing infra contracts
    interface RedisCache { String get(String key); }
    interface DynamoDBTable { Map<String, Object> getItem(String tableName, String partitionKey); }
    interface SesEmailService { String sendEmail(String from, List<String> to, String region); }
    interface DecisionRuleEngine { boolean evaluateVersionAlignment(Map<String, Object> payload); }

    // Service under test
    static class ClaimDecisionCalculationService {
        private final DecisionRuleEngine ruleEngine;
        private final RedisCache redisCache;
        private final DynamoDBTable dynamoDBTable;
        private final SesEmailService sesEmailService;
        private final Logger log;

        ClaimDecisionCalculationService(DecisionRuleEngine ruleEngine, RedisCache redisCache, DynamoDBTable dynamoDBTable, SesEmailService sesEmailService, Logger log) {
            this.ruleEngine = ruleEngine;
            this.redisCache = redisCache;
            this.dynamoDBTable = dynamoDBTable;
            this.sesEmailService = sesEmailService;
            this.log = log;
        }

        boolean processDecisionCalculation(Map<String, Object> payload) {
            if (!validateInput(payload)) {
                log.warn("Input validation failed: invalid payload structure");
                return false;
            }
            boolean aligned = ruleEngine.evaluateVersionAlignment(payload);
            if (!aligned) {
                log.warn("Discrepancy logged: Rule version mismatch for claim {}", payload.get("id"));
                return false;
            }
            redisCache.get("Cache & Reference Data:cache:" + payload.get("id"));
            dynamoDBTable.getItem("Claims & Policy Data Store_table", payload.get("id").toString());
            return true;
        }

        private boolean validateInput(Map<String, Object> payload) {
            return payload.containsKey("id") && payload.containsKey("ruleVersion");
        }
    }
}
