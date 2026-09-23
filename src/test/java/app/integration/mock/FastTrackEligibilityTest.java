package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

import java.util.Map;

/**
 * Mock integration test for Claim Initiation & Routing:calculation:transformation.
 * Validates fast-track eligibility transformation under low-severity conditions.
 */
public class FastTrackEligibilityTest {

    @Mock
    private ClaimTransformationService transformationService;

    private FastTrackRoutingEngine routingEngine;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        routingEngine = new FastTrackRoutingEngine(transformationService);
    }

    @Test
    @DisplayName("transform_to_fast_track_for_low_severity")
    void transform_to_fast_track_for_low_severity() {
        // Arrange: Input parameters as specified in test case
        Map<String, Object> inputPayload = Map.of(
                "tenant_code", "FL01",
                "year", 2024,
                "policy_status", "active",
                "date_of_loss", "2024-05-01",
                "cause_of_loss", "wind",
                "severity", "low",
                "documentation_sufficient", true,
                "attorney_involved", false,
                "public_adjuster_involved", false,
                "catastrophe_event", null
        );

        // Expected transformation result
        Map<String, Object> expectedOutput = Map.of(
                "claim_number", "CLM-FL01-2024-0001",
                "claim_type", "Fast-track claim",
                "routing_target", "Desk Review",
                "manual_intake_review_required", false
        );

        // Mock external transformation service (simulates rules engine & data standardization)
        when(transformationService.transform(anyMap())).thenReturn(expectedOutput);

        // Act
        Map<String, Object> result = routingEngine.processClaimInitiation(inputPayload);

        // Assert: Verify expected results
        assertNotNull(result, "Result should not be null");
        assertEquals("CLM-FL01-2024-0001", result.get("claim_number"), "Claim number should be generated");
        assertEquals("Fast-track claim", result.get("claim_type"), "Claim type should be set to Fast-track claim");
        assertEquals("Desk Review", result.get("routing_target"), "Should be routed directly to Desk Review");
        assertFalse((Boolean) result.get("manual_intake_review_required"), "No manual intake review task required");

        verify(transformationService, times(1)).transform(anyMap());
    }
}
