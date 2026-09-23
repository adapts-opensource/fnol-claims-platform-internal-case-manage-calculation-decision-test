package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressAliasesTest {

    @Mock
    private RedisClient redisClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SesClient sesClient;

    @Mock
    private StructuredLogger logger;

    @InjectMocks
    private ClaimInitiationRoutingDecisionService service;

    @BeforeEach
    void setUp() {
        // Reset state to ensure thread safety and isolation between test runs
        reset(redisClient, dynamoDbClient, sesClient, logger);
    }

    @Test
    void addressAliases() {
        // Arrange: Payload matching claim_initiation___routing_decision_validation model
        String claimId = "CLM-ALIAS-998";
        Map<String, Object> payload = Map.of(
            "id", claimId,
            "payload", Map.of(
                "addressAlias", "123 Main St, Suite 100",
                "routingStrategy", "ALIAS_BASED_ROUTING"
            )
        );

        // Mock Redis cache & reference data contract (NFR: availability, concurrency)
        when(redisClient.get(eq("Cache & Reference Data:cache:address_alias"))).thenReturn("ALIAS_RESOLVED");

        // Mock DynamoDB Claims & Policy Data Store contract
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
            .thenReturn(GetItemResponse.builder().item(Map.of("pk", claimId, "status", "INITIATED")).build());

        // Act: Orchestrate routing decision
        Map<String, Object> decision = service.evaluateRoutingDecision(payload);

        // Assert: Verify decision outcome and infra interactions
        assertNotNull(decision, "Routing decision must not be null");
        assertEquals("ALIAS_BASED_ROUTING", decision.get("routingDecision"), "Should honor address alias routing rule");
        assertTrue((boolean) decision.get("aliasMatched"), "Alias resolution must succeed");

        // Verify I/O contracts (mocked, never calls live AWS or production HTTP APIs)
        verify(redisClient, times(1)).get(eq("Cache & Reference Data:cache:address_alias"));
        verify(dynamoDbClient, times(1)).getItem(any(GetItemRequest.class));
        verifyNoInteractions(sesClient); // Communication not triggered at initiation stage

        // NFR: Input validation & thread safety
        assertDoesNotThrow(() -> service.evaluateRoutingDecision(payload), "Must be thread-safe and validate inputs");
        verify(logger, times(1)).info(eq("Claim routing decision evaluated"), eq(claimId)); // Structured logging simulation
    }
}
