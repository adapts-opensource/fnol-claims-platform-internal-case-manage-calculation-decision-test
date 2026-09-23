package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock tests for Multi-Channel FNOL Submission state transition calculation.
 * Verifies rule application for moratorium and storm event scenarios.
 */
@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionStateTransitionCalculationMockTest {

    @Mock
    private StormEventService stormEventService;

    @Mock
    private MoratoriumService moratoriumService;

    @InjectMocks
    private StateTransitionCalculator stateTransitionCalculator;

    @Test
    void decision_moratorium_active_rule_if_dol_within_storm_event_and_moratorium_active_n_expected_outcome_flag_for_regulatory_review_may_block_claim() {
        // Given: Payload with DoL within storm event and moratorium active
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "fnol-12345");
        payload.put("dateOfLoss", "2024-05-20");
        payload.put("stormEvents", List.of("Storm-Alpha"));
        payload.put("moratoriumActive", true);

        // Mock external dependencies to simulate rule conditions
        when(stormEventService.isWithinStormEvent("2024-05-20", "Storm-Alpha")).thenReturn(true);
        when(moratoriumService.isActive()).thenReturn(true);

        // When: Calculate state transition
        Map<String, Object> resultPayload = stateTransitionCalculator.calculate(payload);

        // Then: Verify expected outcome flag for regulatory review and potential block
        assertNotNull(resultPayload, "Result payload must not be null");
        assertEquals("FLAGGED_FOR_REGULATORY_REVIEW", resultPayload.get("regulatoryReviewFlag"),
                "Outcome should be flagged for regulatory review");
        assertTrue((Boolean) resultPayload.get("mayBlockClaim"),
                "Claim may be blocked due to regulatory review flag");

        // Verify dependencies were invoked as expected
        verify(stormEventService, times(1)).isWithinStormEvent("2024-05-20", "Storm-Alpha");
        verify(moratoriumService, times(1)).isActive();
    }
}
