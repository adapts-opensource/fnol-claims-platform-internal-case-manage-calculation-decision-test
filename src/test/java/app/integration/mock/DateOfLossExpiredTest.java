package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionValidationTest {

    private static final String CACHE_KEY_NAMESPACE = "Cache & Reference Data:cache:";
    private static final String DYNAMODB_TABLE_NAME = "Claims & Policy Data Store_table";
    private static final String PARTITION_KEY = "pk";

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private DynamoDBService dynamoDBService;

    private ClaimRoutingDecisionService sut;

    @BeforeEach
    void setUp() {
        sut = new ClaimRoutingDecisionService(redisCacheService, dynamoDBService);
    }

    @Test
    void validate_date_of_loss_outside_policy_period() {
        // Arrange
        String policyNumber = "POL-200";
        Instant lossDate = Instant.parse("2024-06-20T12:00:00Z");
        Instant policyExpiration = Instant.parse("2024-06-15T23:59:59Z");
        String causeOfLoss = "wind";
        String product = "HO3";

        String cacheKey = CACHE_KEY_NAMESPACE + "policy:" + policyNumber;
        String cachedPolicyPayload = String.format("{\"policyNumber\":\"%s\",\"expiration\":\"%s\"}", policyNumber, policyExpiration);
        when(redisCacheService.get(eq(cacheKey))).thenReturn(cachedPolicyPayload);

        Map<String, Object> auditItem = Map.of(PARTITION_KEY, "claim:" + policyNumber, "status", "Coverage Triage");
        when(dynamoDBService.putItem(eq(DYNAMODB_TABLE_NAME), anyMap())).thenReturn(true);

        // Act
        RoutingDecision decision = sut.validateClaimRouting(policyNumber, lossDate, policyExpiration, causeOfLoss, product);

        // Assert
        assertNotNull(decision, "Routing decision should not be null");
        assertEquals("Coverage Triage", decision.getStatus(), "Claim status should be set to Coverage Triage");
        assertEquals("Coverage review claim", decision.getInitialClaimType(), "Initial claim type should be Coverage review claim");
        assertTrue(decision.getTasks().contains("Review Coverage"), "Task Review Coverage should be created");
        assertTrue(decision.isClaimCaptured(), "Claim should be captured despite date mismatch");

        // Verify infra I/O contracts
        verify(redisCacheService).get(eq(cacheKey));
        verify(dynamoDBService).putItem(eq(DYNAMODB_TABLE_NAME), argThat(item -> item.containsKey(PARTITION_KEY)));
    }

    // Minimal SUT & DTOs for test context
    static class ClaimRoutingDecisionService {
        private final RedisCacheService redisCacheService;
        private final DynamoDBService dynamoDBService;

        ClaimRoutingDecisionService(RedisCacheService redisCacheService, DynamoDBService dynamoDBService) {
            this.redisCacheService = redisCacheService;
            this.dynamoDBService = dynamoDBService;
        }

        RoutingDecision validateClaimRouting(String policyNumber, Instant lossDate, Instant policyExpiration, String causeOfLoss, String product) {
            RoutingDecision decision = new RoutingDecision();
            decision.setClaimCaptured(true);
            if (!lossDate.isBefore(policyExpiration)) {
                decision.setStatus("Coverage Triage");
                decision.setInitialClaimType("Coverage review claim");
                decision.setTasks(List.of("Review Coverage"));
            } else {
                decision.setStatus("Accepted");
                decision.setInitialClaimType("Standard claim");
                decision.setTasks(List.of());
            }
            return decision;
        }
    }

    static class RoutingDecision {
        private String status;
        private String initialClaimType;
        private List<String> tasks;
        private boolean claimCaptured;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getInitialClaimType() { return initialClaimType; }
        public void setInitialClaimType(String initialClaimType) { this.initialClaimType = initialClaimType; }
        public List<String> getTasks() { return tasks; }
        public void setTasks(List<String> tasks) { this.tasks = tasks; }
        public boolean isClaimCaptured() { return claimCaptured; }
        public void setClaimCaptured(boolean claimCaptured) { this.claimCaptured = claimCaptured; }
    }

    interface RedisCacheService { String get(String key); }
    interface DynamoDBService { boolean putItem(String table, Map<String, Object> item); }
}
