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
public class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private DecisionCalculationService decisionCalculationService;

    private Map<String, Object> payloadMissingSeverity;

    @BeforeEach
    void setUp() {
        payloadMissingSeverity = Map.of(
            "id", "CLM-INIT-001",
            "payload", Map.of(
                "policy_number", "POL-12345",
                "incident_date", "2024-01-10",
                "claim_type", "AUTO_COLLISION"
                // severity_estimate is intentionally omitted per test case
            )
        );
    }

    @Test
    void severity_estimate_missing() {
        // Arrange: Mock the decision calculation service to simulate validation failure
        when(decisionCalculationService.calculateRoutingDecision(payloadMissingSeverity))
            .thenThrow(new IllegalArgumentException("Required field 'severity_estimate' is missing from claim payload"));

        // Act & Assert: Verify that an exception is thrown when severity estimate is absent
        IllegalArgumentException thrown = assertThrows(
            IllegalArgumentException.class,
            () -> decisionCalculationService.calculateRoutingDecision(payloadMissingSeverity)
        );

        assertEquals("Required field 'severity_estimate' is missing from claim payload", thrown.getMessage());

        // Verify: Ensure the service was invoked exactly once
        verify(decisionCalculationService, times(1)).calculateRoutingDecision(payloadMissingSeverity);
    }
}
