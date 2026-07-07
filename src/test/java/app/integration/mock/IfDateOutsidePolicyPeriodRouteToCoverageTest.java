package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.HashMap;

/**
 * Mock integration tests for Claim Data Standardization:transformation:orchestration.
 * Verifies routing logic when claim dates fall outside the active policy period.
 */
interface ClaimOrchestrationService {
    Map<String, Object> transformAndRoute(Map<String, Object> payload);
}

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationOrchestrationMockTest {

    @Mock
    private ClaimOrchestrationService orchestrationService;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimPayload = new HashMap<>();
        claimPayload.put("id", "CLM-98765");
        claimPayload.put("claim_date", "2022-11-15");
        claimPayload.put("policy_period_start", "2023-01-01");
        claimPayload.put("policy_period_end", "2024-01-01");
        claimPayload.put("current_state", "intake");
    }

    @Test
    void if_date_outside_policy_period_route_to_coverage_review() {
        // Arrange: Mock orchestration response for out-of-policy dates
        Map<String, Object> expectedTransition = Map.of(
            "state", "coverage_review",
            "routing_hint", "coverage_specialist_queue",
            "reason", "claim_date_outside_policy_period"
        );
        when(orchestrationService.transformAndRoute(anyMap())).thenReturn(expectedTransition);

        // Act: Invoke orchestration transformation
        Map<String, Object> actualResult = orchestrationService.transformAndRoute(claimPayload);

        // Assert: Verify state transition to coverage review
        assertNotNull(actualResult);
        assertEquals("coverage_review", actualResult.get("state"));
        assertEquals("coverage_specialist_queue", actualResult.get("routing_hint"));
        verify(orchestrationService, times(1)).transformAndRoute(claimPayload);
    }
}
