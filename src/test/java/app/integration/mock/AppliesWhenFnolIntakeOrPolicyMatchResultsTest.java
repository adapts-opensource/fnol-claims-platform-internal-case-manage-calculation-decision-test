package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private RedisCacheClient redisCacheClient;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private EmailNotificationService emailNotificationService;

    @InjectMocks
    private ClaimDecisionValidationService claimDecisionValidationService;

    private static final String CLAIM_ID = "claim-12345";
    private static final String CACHE_KEY_NAMESPACE = "Cache & Reference Data:cache:";
    private static final int DEFAULT_TTL_SECONDS = 3600;
    private static final String TABLE_NAME = "Claims & Policy Data Store_table";
    private static final String PARTITION_KEY = "pk";
    private static final String SES_REGION = "us-east-1";
    private static final String FROM_ADDRESS = "noreply@newcoinsurance.com";

    @BeforeEach
    void setUp() {
        // Reset mock behaviors to ensure clean state per test
        reset(redisCacheClient, dynamoDbClient, emailNotificationService);
    }

    @Test
    void applies_when_fnol_intake_or_policy_match_results_are_available() {
        // Arrange: Mock external I/O to simulate available FNOL intake or policy match results
        when(redisCacheClient.get(CACHE_KEY_NAMESPACE + "fnol-intake"))
                .thenReturn("fnol-intake-complete");
        when(dynamoDbClient.getItem(TABLE_NAME, PARTITION_KEY, CLAIM_ID))
                .thenReturn(Map.of(
                        "status", "policy_match_found",
                        "payload", Map.of("policyId", "POL-98765", "matchScore", 0.95)
                ));

        // Act: Invoke the decision validation logic
        boolean decisionApplied = claimDecisionValidationService.evaluateRoutingDecision(CLAIM_ID);

        // Assert: Verify that the decision is applied when results are available
        assertTrue(decisionApplied, "Decision should be applied when FNOL intake or policy match results are available");

        // Verify: Ensure external I/O contracts were respected and no premature side-effects occurred
        verify(redisCacheClient).get(CACHE_KEY_NAMESPACE + "fnol-intake");
        verify(dynamoDbClient).getItem(TABLE_NAME, PARTITION_KEY, CLAIM_ID);
        verifyNoInteractions(emailNotificationService); // Email not triggered until routing is finalized
    }
}
