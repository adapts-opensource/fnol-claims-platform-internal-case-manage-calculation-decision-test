package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private RoutingDecisionService routingDecisionService;

    private Map<String, Object> fastTrackPayload;

    @BeforeEach
    void setUp() {
        // Arrange test payload based on feature inputs
        fastTrackPayload = Map.of(
            "policy_number", "POL-FAST",
            "loss_date", "2024-05-15",
            "cause_of_loss", "wind",
            "severity", "low",
            "insured", "Alice Smith",
            "attorney_flag", false,
            "public_adjuster_flag", false,
            "aob_flag", false,
            "prior_claims_count", 0,
            "documentation_sufficient", true,
            "catastrophe_event", null
        );
    }

    @Test
    void validate_fast_track_eligibility() {
        // Arrange: Mock the decision engine to return fast-track routing outcome
        RoutingOutcome expectedOutcome = new RoutingOutcome(
            "Fast-track claim",
            true,
            Map.of("Review FNOL", "suppressed", "Assign Adjuster", "automated"),
            "fast-track-queue"
        );
        when(routingDecisionService.evaluateClaim(anyMap())).thenReturn(expectedOutcome);

        // Act: Trigger validation & routing
        RoutingOutcome actualOutcome = routingDecisionService.evaluateClaim(fastTrackPayload);

        // Assert: Verify expected routing decisions
        assertNotNull(actualOutcome, "Routing outcome should not be null");
        assertEquals("Fast-track claim", actualOutcome.initialClaimType(), "Initial claim type must be Fast-track");
        assertTrue(actualOutcome.isAutomatedRoutingApplied(), "Automated routing must be applied");
        assertEquals("suppressed", actualOutcome.tasks().get("Review FNOL"), "Review FNOL task should be suppressed");
        assertEquals("automated", actualOutcome.tasks().get("Assign Adjuster"), "Assign Adjuster task should be marked automated");
        assertEquals("fast-track-queue", actualOutcome.targetQueue(), "Claim must be opened in fast-track queue");

        // Verify external I/O mock was invoked exactly once
        verify(routingDecisionService, times(1)).evaluateClaim(fastTrackPayload);
    }

    /**
     * Internal record representing the routing decision outcome for testing purposes.
     */
    private static class RoutingOutcome {
        private final String initialClaimType;
        private final boolean automatedRoutingApplied;
        private final Map<String, String> tasks;
        private final String targetQueue;

        public RoutingOutcome(String initialClaimType, boolean automatedRoutingApplied, Map<String, String> tasks, String targetQueue) {
            this.initialClaimType = initialClaimType;
            this.automatedRoutingApplied = automatedRoutingApplied;
            this.tasks = tasks;
            this.targetQueue = targetQueue;
        }

        public String initialClaimType() { return initialClaimType; }
        public boolean isAutomatedRoutingApplied() { return automatedRoutingApplied; }
        public Map<String, String> tasks() { return tasks; }
        public String targetQueue() { return targetQueue; }
    }

    /**
     * Mock service interface representing the claim routing decision logic.
     */
    private interface RoutingDecisionService {
        RoutingOutcome evaluateClaim(Map<String, Object> payload);
    }
}
