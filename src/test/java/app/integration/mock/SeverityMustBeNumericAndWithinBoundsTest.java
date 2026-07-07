package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimRoutingDecisionCalculationMockTest {

    private static final String SEVERITY_KEY = "severity";
    private static final int MIN_SEVERITY = 1;
    private static final int MAX_SEVERITY = 10;

    @Mock
    private RedisClient redisClient;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private SesClient sesClient;

    private ClaimCalculationService calculationService;

    @BeforeEach
    void setUp() {
        calculationService = new ClaimCalculationService(redisClient, dynamoDbClient, sesClient);
    }

    @Test
    void severity_must_be_numeric_and_within_bounds() {
        // Arrange: Valid numeric severity within bounds
        Map<String, Object> validPayload = Map.of("id", "claim-001", "severity", 5);
        setupInfraMocks("claim-001");
        assertDoesNotThrow(() -> calculationService.calculateDecision(validPayload), "Valid numeric severity should pass validation");

        // Arrange: Non-numeric severity
        Map<String, Object> nonNumericPayload = Map.of("id", "claim-002", "severity", "high");
        setupInfraMocks("claim-002");
        assertThrows(IllegalArgumentException.class, () -> calculationService.calculateDecision(nonNumericPayload), "Non-numeric severity should throw validation error");

        // Arrange: Severity below minimum bounds
        Map<String, Object> belowBoundsPayload = Map.of("id", "claim-003", "severity", 0);
        setupInfraMocks("claim-003");
        assertThrows(IllegalArgumentException.class, () -> calculationService.calculateDecision(belowBoundsPayload), "Severity below bounds should throw validation error");

        // Arrange: Severity above maximum bounds
        Map<String, Object> aboveBoundsPayload = Map.of("id", "claim-004", "severity", 11);
        setupInfraMocks("claim-004");
        assertThrows(IllegalArgumentException.class, () -> calculationService.calculateDecision(aboveBoundsPayload), "Severity above bounds should throw validation error");
    }

    private void setupInfraMocks(String claimId) {
        when(redisClient.get(anyString())).thenReturn("reference_data_v1");
        when(dynamoDbClient.getItem(anyString(), anyString())).thenReturn(Map.of("pk", claimId, "status", "INITIATED"));
        doNothing().when(sesClient).sendEmail(anyString(), anyList(), anyString());
    }

    // Package-private interfaces representing external infra contracts
    interface RedisClient {
        String get(String key);
    }

    interface DynamoDbClient {
        Map<String, Object> getItem(String tableName, String partitionKey);
    }

    interface SesClient {
        void sendEmail(String fromAddress, List<String> toAddresses, String region);
    }

    // Service under test handling calculation & routing decision logic
    static class ClaimCalculationService {
        private final RedisClient redisClient;
        private final DynamoDbClient dynamoDbClient;
        private final SesClient sesClient;

        ClaimCalculationService(RedisClient redisClient, DynamoDbClient dynamoDbClient, SesClient sesClient) {
            this.redisClient = redisClient;
            this.dynamoDbClient = dynamoDbClient;
            this.sesClient = sesClient;
        }

        void calculateDecision(Map<String, Object> payload) {
            // Observability NFR: Structured logging placeholder
            System.out.println("[STRUCTURED_LOG] {\"event\":\"calculation_start\",\"feature\":\"Claim Initiation & Routing:decision:calculation\"}");

            // Security NFR: Input validation
            Object severityObj = payload.get(SEVERITY_KEY);
            if (severityObj == null || !(severityObj instanceof Number)) {
                throw new IllegalArgumentException("Severity must be numeric");
            }

            int severity = ((Number) severityObj).intValue();
            if (severity < MIN_SEVERITY || severity > MAX_SEVERITY) {
                throw new IllegalArgumentException("Severity must be within bounds [" + MIN_SEVERITY + ", " + MAX_SEVERITY + "]");
            }

            // Infra I/O Contract mocks (Redis, DynamoDB, SES)
            String cachedValue = redisClient.get("Cache & Reference Data:cache:");
            Map<String, Object> itemPayload = dynamoDbClient.getItem("Claims & Policy Data Store_table", "pk");
            sesClient.sendEmail("noreply@newco.insurance", List.of("admin@newco.insurance"), "us-east-1");

            // Routing decision proceeds...
        }
    }
}
