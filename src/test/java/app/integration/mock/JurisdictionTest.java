package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock test for Claim Initiation & Routing:decision:calculation feature.
 * Verifies jurisdiction-based routing logic with mocked external I/O.
 * NFRs: thread_safety (mock isolation), input_validation, structured_logging (mocked), security (input sanitization).
 */
@ExtendWith(MockitoExtension.class)
public class ClaimRoutingDecisionCalculationMockTest {

    @Mock
    private RedisClient redisClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private SesEmailService sesEmailService;

    @InjectMocks
    private RoutingDecisionCalculationService routingDecisionService;

    private Map<String, Object> validPayload;
    private String jurisdiction;

    @BeforeEach
    void setUp() {
        jurisdiction = "CA";
        validPayload = Map.of(
                "id", UUID.randomUUID().toString(),
                "jurisdiction", jurisdiction,
                "claimType", "auto",
                "priority", "standard",
                "insuredState", "CA"
        );
    }

    @Test
    void jurisdiction() {
        // Arrange: Mock external I/O contracts (Redis, DynamoDB, SES)
        when(redisClient.get(eq("Cache & Reference Data:cache:jurisdiction_rules")))
                .thenReturn("CA:WEST_COAST_ROUTER");
        when(dynamoDbClient.getItem(eq("Claims & Policy Data Store_table"), eq("pk"), eq("jurisdiction_config")))
                .thenReturn(Map.of("status", "ACTIVE", "region", "WEST"));
        when(sesEmailService.send(anyString(), anyList(), anyString()))
                .thenReturn("msg-id-" + UUID.randomUUID());

        // Act: Execute calculation with jurisdiction payload
        Map<String, Object> result = routingDecisionService.calculate(validPayload);

        // Assert: Verify jurisdiction impacts routing decision
        assertNotNull(result, "Routing decision must not be null");
        assertEquals("WEST_COAST_ROUTER", result.get("routingTarget"), "Jurisdiction must map to correct router");
        assertEquals(jurisdiction, result.get("jurisdiction"), "Original jurisdiction must be preserved");
        assertTrue(result.containsKey("validated"), "Validation flag must be present");
        assertTrue((boolean) result.get("validated"), "Input validation must pass for valid jurisdiction");

        // Verify I/O interactions
        verify(redisClient).get(anyString());
        verify(dynamoDbClient).getItem(anyString(), anyString(), anyString());
        verify(sesEmailService).send(anyString(), anyList(), anyString());
    }
}
