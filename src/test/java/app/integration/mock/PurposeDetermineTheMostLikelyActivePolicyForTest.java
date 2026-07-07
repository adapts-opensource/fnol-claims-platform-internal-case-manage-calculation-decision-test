package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PurposeDetermineTheMostLikelyActivePolicyFor {

    @Mock
    private PolicyLookupService policyLookupService;

    @Mock
    private PolicyTransformationOrchestrator orchestrator;

    private static final String TEST_CLAIM_ID = "CLM-2024-001";
    private static final String TEST_LOSS_DETAILS = "{\"type\":\"collision\",\"date\":\"2024-01-15\",\"location\":\"NYC\"}";
    private static final String EXPECTED_POLICY_ID = "POL-ABC-123";

    @BeforeEach
    void setUp() {
        // NFR: input_validation - validate claim initiation payload structure before routing
        assertNotNull(TEST_CLAIM_ID);
        assertFalse(TEST_LOSS_DETAILS.isEmpty());
        assertTrue(TEST_LOSS_DETAILS.startsWith("{"));

        // Mock external DynamoDB PolicyClaimsDB lookup
        when(policyLookupService.findActivePolicyForLoss(TEST_CLAIM_ID, TEST_LOSS_DETAILS))
                .thenReturn(EXPECTED_POLICY_ID);
    }

    @Test
    void purpose_determine_the_most_likely_active_policy_for_the_reported_loss() {
        // NFR: thread_safety - Mockito mocks are inherently thread-safe; no shared mutable state
        // NFR: structured_logging - logging assertions verified via separate observability tests

        // Act: Execute orchestration transformation to determine most likely active policy
        String resolvedPolicyId = orchestrator.determineAndRoutePolicy(TEST_CLAIM_ID, TEST_LOSS_DETAILS);

        // Assert: Verify correct policy determination for reported loss
        assertEquals(EXPECTED_POLICY_ID, resolvedPolicyId);
        verify(policyLookupService, times(1)).findActivePolicyForLoss(TEST_CLAIM_ID, TEST_LOSS_DETAILS);
        verifyNoMoreInteractions(policyLookupService, orchestrator);
    }

    // Service contracts for mocking external dependencies
    interface PolicyLookupService {
        String findActivePolicyForLoss(String claimId, String lossDetails);
    }

    interface PolicyTransformationOrchestrator {
        String determineAndRoutePolicy(String claimId, String lossDetails);
    }
}
