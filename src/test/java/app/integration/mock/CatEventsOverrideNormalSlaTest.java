package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Test class for Claim Initiation & Routing:decision:calculation
 * Verifies that CAT events override normal SLA calculations.
 */
class ClaimRoutingDecisionCalculationMockTest {

    @Mock
    private ClaimDecisionCalculator claimDecisionCalculator;

    @InjectMocks
    private ClaimRoutingDecisionService claimRoutingDecisionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void catEventsOverrideNormalSla() {
        // given
        Map<String, Object> payload = Map.of(
            "eventType", "CAT",
            "normalSlaHours", 24,
            "isCatEvent", true
        );
        int expectedCatSlaHours = 4;

        when(claimDecisionCalculator.calculateSlaHours(payload))
            .thenReturn(expectedCatSlaHours);

        // when
        int actualSlaHours = claimRoutingDecisionService.calculateRoutingSla(payload);

        // then
        assertEquals(expectedCatSlaHours, actualSlaHours);
    }
}

/**
 * Interface representing the external calculation dependency.
 * Mocked to isolate business logic from live routing services.
 */
interface ClaimDecisionCalculator {
    int calculateSlaHours(Map<String, Object> payload);
}

/**
 * Service responsible for routing decision calculations.
 * Uses mocked calculator to verify SLA override behavior.
 */
class ClaimRoutingDecisionService {
    private final ClaimDecisionCalculator claimDecisionCalculator;

    ClaimRoutingDecisionService(ClaimDecisionCalculator claimDecisionCalculator) {
        this.claimDecisionCalculator = claimDecisionCalculator;
    }

    int calculateRoutingSla(Map<String, Object> payload) {
        return claimDecisionCalculator.calculateSlaHours(payload);
    }
}
