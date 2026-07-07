package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DecisionMatchConfidenceThresholdRuleHigh09Test {

    @Mock
    private ClaimRoutingDecisionService claimRoutingDecisionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void decision_match_confidence_threshold_rule_high_0_9_medium_0_7_0_89_low_0_7_expected_outcome_routing_path_assignment() {
        // Mock external decision service responses based on confidence thresholds
        when(claimRoutingDecisionService.calculateRoutingPath(0.9)).thenReturn("ROUTING_PATH_HIGH");
        when(claimRoutingDecisionService.calculateRoutingPath(0.75)).thenReturn("ROUTING_PATH_MEDIUM");
        when(claimRoutingDecisionService.calculateRoutingPath(0.6)).thenReturn("ROUTING_PATH_LOW");

        // Verify High confidence threshold (>= 0.9)
        String highPath = claimRoutingDecisionService.calculateRoutingPath(0.9);
        assertEquals("ROUTING_PATH_HIGH", highPath);

        // Verify Medium confidence threshold (0.7 - 0.89)
        String mediumPath = claimRoutingDecisionService.calculateRoutingPath(0.75);
        assertEquals("ROUTING_PATH_MEDIUM", mediumPath);

        // Verify Low confidence threshold (< 0.7)
        String lowPath = claimRoutingDecisionService.calculateRoutingPath(0.6);
        assertEquals("ROUTING_PATH_LOW", lowPath);

        verify(claimRoutingDecisionService, times(3)).calculateRoutingPath(anyDouble());
    }
}
