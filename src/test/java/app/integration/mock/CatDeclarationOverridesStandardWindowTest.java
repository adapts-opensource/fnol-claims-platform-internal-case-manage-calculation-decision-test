package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 mock test for Claim Initiation & Routing:decision:calculation.
 * Verifies that a CAT declaration overrides the standard processing window.
 * All external I/O (DynamoDB, Redis) is mocked to comply with security and compliance NFRs.
 */
@ExtendWith(MockitoExtension.class)
public class CatDeclarationOverridesStandardWindowTest {

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private RoutingDecisionCalculator routingDecisionCalculator;

    private Map<String, Object> validationPayload;

    @BeforeEach
    void setUp() {
        // Aligns with claim_initiation___routing_decision_validation data model
        validationPayload = Map.of(
                "id", "dec-calc-001",
                "payload", Map.of(
                        "claimId", "CLM-98765",
                        "region", "us-east-1",
                        "isCatDeclarationActive", true,
                        "standardWindowDays", 5,
                        "catOverrideWindowDays", 10
                )
        );
    }

    @Test
    void cat_declaration_overrides_standard_window() {
        // Given: External I/O contracts return CAT declaration active and standard window config
        when(dynamoDbService.getItem("Cache & Reference Data_dynamodb", Map.of("pk", "cat_status", "sk", "us-east-1")))
                .thenReturn(Map.of("active", true));
        when(redisCacheService.get("Cache & Reference Data_elasticache:cache:standard_window:us-east-1"))
                .thenReturn("5");

        // When: Routing decision calculation is executed
        Map<String, Object> decision = routingDecisionCalculator.calculate(validationPayload);

        // Then: CAT declaration overrides standard window in the decision payload
        assertNotNull(decision);
        assertEquals(10, decision.get("effectiveRoutingWindowDays"));
        assertEquals("CAT_DECLARATION_ROUTING", decision.get("routingDecision"));

        // Verify mocked infra I/O was invoked exactly once (thread-safe, no live calls)
        verify(dynamoDbService).getItem("Cache & Reference Data_dynamodb", Map.of("pk", "cat_status", "sk", "us-east-1"));
        verify(redisCacheService).get("Cache & Reference Data_elasticache:cache:standard_window:us-east-1");
        verifyNoMoreInteractions(dynamoDbService, redisCacheService);
    }
}
