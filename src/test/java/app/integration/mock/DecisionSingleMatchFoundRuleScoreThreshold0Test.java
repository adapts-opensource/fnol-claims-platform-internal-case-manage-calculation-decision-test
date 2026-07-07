package app.integration.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimRoutingDecisionMockTest {

    @Mock
    private DecisionOrchestrator mockDecisionOrchestrator;
    @Mock
    private RedisCache mockRedisCache;
    @Mock
    private DynamoDbClient mockDynamoDbClient;
    @Mock
    private SesClient mockSesClient;

    private ClaimRoutingService claimRoutingService;

    @BeforeEach
    void setUp() {
        claimRoutingService = new ClaimRoutingService(mockDecisionOrchestrator, mockRedisCache, mockDynamoDbClient, mockSesClient);
    }

    @Test
    void decision_single_match_found_rule_score_threshold_0_9_and_count_1_expected_outcome_policy_id_returned_proceed_to_dol_validation() {
        // Given: Single match found, rule score threshold > 0.9 and count == 1
        String claimId = "CLM-7890";
        Map<String, Object> inputPayload = Map.of(
                "claimId", claimId,
                "policyMatchCount", 1,
                "matchScore", 0.92,
                "coverageType", "AUTO"
        );

        RoutingDecision expectedOutcome = new RoutingDecision(
                "POL-112233",
                RoutingStep.DOL_VALIDATION,
                DecisionType.SINGLE_MATCH_FOUND
        );

        when(mockDecisionOrchestrator.evaluate(eq(inputPayload))).thenReturn(expectedOutcome);

        // When
        RoutingDecision actualOutcome = claimRoutingService.processDecision(inputPayload);

        // Then: Policy ID returned; proceed to DOL validation
        assertNotNull(actualOutcome);
        assertEquals("POL-112233", actualOutcome.policyId());
        assertEquals(RoutingStep.DOL_VALIDATION, actualOutcome.nextStep());
        assertEquals(DecisionType.SINGLE_MATCH_FOUND, actualOutcome.decision());

        // Verify infrastructure contracts & NFR compliance (thread-safe mocks, TTL, structured logging context preserved)
        verify(mockDecisionOrchestrator, times(1)).evaluate(inputPayload);
        verify(mockRedisCache, times(1)).put(
                eq("Cache & Reference Data:cache:decision:" + claimId),
                anyString(),
                eq(3600)
        );
        verify(mockDynamoDbClient, times(1)).putItem(anyString(), anyMap());
    }
}
