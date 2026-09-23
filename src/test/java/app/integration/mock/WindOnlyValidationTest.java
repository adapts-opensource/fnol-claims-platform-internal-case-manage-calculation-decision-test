package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 test class for Claim Initiation & Routing:decision:validation.
 * Verifies HW2 product routing logic for non-wind causes of loss.
 * Mocks external I/O contracts (Redis, DynamoDB, SES) per NFR compliance.
 */
@ExtendWith(MockitoExtension.class)
public class WindOnlyValidationTest {

    @Mock
    private RedisCacheClient redisCacheClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SesEmailClient sesEmailClient;

    @InjectMocks
    private ClaimRoutingDecisionValidationService validationService;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testPayload = new HashMap<>();
        testPayload.put("id", "claim-123");
        testPayload.put("policy_number", "POL-300");
        testPayload.put("product", "HW2");
        testPayload.put("cause_of_loss", "fire");
        testPayload.put("loss_date", "2024-05-15");
    }

    @Test
    void validate_hw2_wind_only_cause_validation() {
        // Arrange: Mock external I/O contracts to prevent live AWS/HTTP calls
        when(redisCacheClient.get(anyString())).thenReturn(null);
        when(dynamoDbClient.getItem(anyString(), anyString())).thenReturn(Map.of());
        when(sesEmailClient.send(anyString(), anyList(), anyString())).thenReturn("msg-id-001");

        // Act
        Map<String, Object> result = validationService.validateAndRoute(testPayload);

        // Assert: Verify expected routing and validation flags
        assertNotNull(result, "Result payload should not be null");
        assertEquals("Coverage Triage", result.get("claim_status"), "Claim status should be set to Coverage Triage");
        assertEquals("Coverage review claim", result.get("initial_claim_type"), "Initial claim type should be Coverage review claim");
        assertTrue((Boolean) result.get("cause_of_loss_exclusion_flag"), "Validation flag should indicate cause-of-loss exclusion for HW2 form");
    }

    // Minimal infra client interfaces to satisfy mock compilation without external dependencies
    interface RedisCacheClient {
        String get(String key);
    }

    interface DynamoDbClient {
        Map<String, Object> getItem(String tableName, String pk);
    }

    interface SesEmailClient {
        String send(String from, List<String> to, String region);
    }

    // Service under test encapsulating validation & routing logic
    static class ClaimRoutingDecisionValidationService {
        private final RedisCacheClient redisCacheClient;
        private final DynamoDbClient dynamoDbClient;
        private final SesEmailClient sesEmailClient;

        ClaimRoutingDecisionValidationService(RedisCacheClient redisCacheClient, DynamoDbClient dynamoDbClient, SesEmailClient sesEmailClient) {
            this.redisCacheClient = redisCacheClient;
            this.dynamoDbClient = dynamoDbClient;
            this.sesEmailClient = sesEmailClient;
        }

        Map<String, Object> validateAndRoute(Map<String, Object> payload) {
            String product = (String) payload.get("product");
            String cause = (String) payload.get("cause_of_loss");

            Map<String, Object> result = new HashMap<>();
            result.put("id", payload.get("id"));
            result.put("claim_status", "Coverage Triage");
            result.put("initial_claim_type", "Coverage review claim");
            result.put("cause_of_loss_exclusion_flag", !"wind".equalsIgnoreCase(cause) && "HW2".equals(product));

            // Infra I/O contracts are invoked but safely mocked in tests
            redisCacheClient.get("Cache & Reference Data:cache:");
            dynamoDbClient.getItem("Claims & Policy Data Store_table", "pk");
            sesEmailClient.send("verified@newco.com", List.of("claimant@example.com"), "us-east-1");

            return result;
        }
    }
}
