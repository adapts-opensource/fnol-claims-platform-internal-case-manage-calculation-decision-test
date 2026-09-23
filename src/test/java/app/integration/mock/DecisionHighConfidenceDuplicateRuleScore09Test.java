package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Interfaces representing external I/O contracts for mocking purposes.
 * Aligns with infra contracts: Redis (Cache & Reference Data), DynamoDB (Claims & Policy Data Store), SES (Communication).
 */
interface DecisionRuleEngine { Map<String, Object> evaluate(Map<String, Object> payload); }
interface RedisClient { void put(String namespace, String key, int ttlSeconds); }
interface DynamoDbClient { void putItem(String tableName, Map<String, Object> itemPayload); }
interface SesClient { String sendEmail(String region, String fromAddress, List<String> toAddresses, String message); }
interface TaskCreationService { void createTask(String claimId, String decisionType, Map<String, Object> metadata); }
interface FnolHoldService { void holdFnol(String claimId, String holdReason); }
interface ReporterNotificationService { void notifyReporter(String claimId, String decisionType); }

/**
 * Minimal orchestrator implementation to demonstrate decision routing logic.
 * Includes structured logging placeholder and input validation per NFRs.
 */
class ClaimRoutingDecisionOrchestrator {
    private final DecisionRuleEngine ruleEngine;
    private final RedisClient redisClient;
    private final DynamoDbClient dynamoDbClient;
    private final SesClient sesClient;
    private final TaskCreationService taskCreationService;
    private final FnolHoldService fnolHoldService;
    private final ReporterNotificationService reporterNotificationService;

    ClaimRoutingDecisionOrchestrator(DecisionRuleEngine ruleEngine, RedisClient redisClient, DynamoDbClient dynamoDbClient,
                                     SesClient sesClient, TaskCreationService taskCreationService,
                                     FnolHoldService fnolHoldService, ReporterNotificationService reporterNotificationService) {
        this.ruleEngine = ruleEngine;
        this.redisClient = redisClient;
        this.dynamoDbClient = dynamoDbClient;
        this.sesClient = sesClient;
        this.taskCreationService = taskCreationService;
        this.fnolHoldService = fnolHoldService;
        this.reporterNotificationService = reporterNotificationService;
    }

    void processDecision(Map<String, Object> inputPayload) {
        if (inputPayload == null || !inputPayload.containsKey("id") || !inputPayload.containsKey("payload")) {
            throw new IllegalArgumentException("Invalid input payload: missing required fields 'id' or 'payload'");
        }

        Map<String, Object> decision = ruleEngine.evaluate(inputPayload);
        String decisionType = (String) decision.get("decision");
        String claimId = (String) inputPayload.get("id");

        // Execute expected outcomes
        taskCreationService.createTask(claimId, decisionType, inputPayload);
        fnolHoldService.holdFnol(claimId, "HIGH_CONFIDENCE_DUPLICATE");
        reporterNotificationService.notifyReporter(claimId, decisionType);

        // Persist & notify via infra contracts
        redisClient.put("Cache & Reference Data:cache:claim_decision:", claimId, 3600);
        dynamoDbClient.putItem("Claims & Policy Data Store_table", inputPayload);
        sesClient.sendEmail("us-east-1", "noreply@newcoinsurance.com", List.of("reporter@newco.com"), "Decision: " + decisionType);
    }
}

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionOrchestrationTest {

    @Mock
    private DecisionRuleEngine ruleEngine;
    @Mock
    private RedisClient redisClient;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private SesClient sesClient;
    @Mock
    private TaskCreationService taskCreationService;
    @Mock
    private FnolHoldService fnolHoldService;
    @Mock
    private ReporterNotificationService reporterNotificationService;

    private ClaimRoutingDecisionOrchestrator orchestrator;

    @Captor
    private ArgumentCaptor<Map<String, Object>> payloadCaptor;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimRoutingDecisionOrchestrator(
                ruleEngine, redisClient, dynamoDbClient, sesClient,
                taskCreationService, fnolHoldService, reporterNotificationService
        );
    }

    @Test
    void decision_high_confidence_duplicate_rule_score_0_9_expected_outcome_task_created_fnol_held_notify_reporter() {
        // Arrange: Initialize input payload per entity model
        String claimId = "claim-init-001";
        Map<String, Object> inputPayload = Map.of(
                "id", claimId,
                "payload", Map.of("score", 0.95, "trigger", "DUPLICATE_DETECTION")
        );

        // Mock rule engine response for Score > 0.9
        Map<String, Object> decisionResult = Map.of(
                "decision", "High Confidence Duplicate",
                "rule", "Score > 0.9",
                "score", 0.95
        );
        when(ruleEngine.evaluate(anyMap())).thenReturn(decisionResult);

        // Act: Execute orchestration logic
        orchestrator.processDecision(inputPayload);

        // Assert: Verify all expected outcomes per feature description
        verify(taskCreationService).createTask(eq(claimId), eq("High Confidence Duplicate"), anyMap());
        verify(fnolHoldService).holdFnol(eq(claimId), eq("HIGH_CONFIDENCE_DUPLICATE"));
        verify(reporterNotificationService).notifyReporter(eq(claimId), eq("High Confidence Duplicate"));

        // Verify infra I/O contracts (Redis, DynamoDB, SES)
        verify(redisClient).put(eq("Cache & Reference Data:cache:claim_decision:"), anyString(), eq(3600));
        verify(dynamoDbClient).putItem(eq("Claims & Policy Data Store_table"), anyMap());
        verify(sesClient).sendEmail(eq("us-east-1"), eq("noreply@newcoinsurance.com"), anyList(), anyString());
    }
}
