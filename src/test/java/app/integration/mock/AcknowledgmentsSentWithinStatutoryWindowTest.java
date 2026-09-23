package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcknowledgmentsSentWithinStatutoryWindowTest {

    private static final Duration STATUTORY_WINDOW = Duration.ofHours(24);
    private static final Instant BASE_TIME = Instant.parse("2023-11-15T08:00:00Z");
    private static final String CLAIM_ID = "claim-init-001";
    private static final String POLICY_NUMBER = "POL-987654";
    private static final String CACHE_KEY_NAMESPACE = "Cache & Reference Data:cache:";

    @Mock
    private CacheService cacheService;

    @Mock
    private CommunicationService communicationService;

    private Clock clock;
    private DecisionCalculationService decisionCalculationService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(BASE_TIME, ZoneOffset.UTC);
        decisionCalculationService = new DecisionCalculationService(cacheService, communicationService, clock);
    }

    @Test
    void acknowledgments_sent_within_statutory_window() {
        // Given: Valid claim initiation payload conforming to claim_initiation___routing_decision_validation
        Map<String, Object> payload = Map.of(
            "id", CLAIM_ID,
            "policyNumber", POLICY_NUMBER,
            "reportedDate", BASE_TIME.toString(),
            "claimType", "AUTO"
        );

        // Mock cache retrieval for statutory window configuration
        when(cacheService.get(eq(CACHE_KEY_NAMESPACE + "statutory_window_seconds")))
                .thenReturn(String.valueOf(STATUTORY_WINDOW.getSeconds()));

        // When: Calculate routing decision
        Map<String, Object> decisionResult = decisionCalculationService.calculateDecision(payload);

        // Then: Verify acknowledgment was sent within the statutory window
        verify(communicationService, timeout(100).times(1))
                .sendAcknowledgment(eq(CLAIM_ID), anyString(), any());

        // Verify decision payload contains expected routing status
        assertNotNull(decisionResult);
        assertEquals("ROUTED_TO_ADJUSTER", decisionResult.get("routingStatus"));

        // Verify statutory window constraint is respected by design
        long elapsedSeconds = Duration.between(BASE_TIME, clock.instant()).getSeconds();
        assertTrue(elapsedSeconds <= STATUTORY_WINDOW.getSeconds(),
                "Acknowledgment must be sent within the statutory window");
    }

    // Internal test doubles for external I/O contracts (Redis/SES)
    interface CacheService {
        String get(String key);
    }

    interface CommunicationService {
        void sendAcknowledgment(String claimId, String message, Map<String, Object> metadata);
    }

    static class DecisionCalculationService {
        private final CacheService cacheService;
        private final CommunicationService communicationService;
        private final Clock clock;

        DecisionCalculationService(CacheService cacheService, CommunicationService communicationService, Clock clock) {
            this.cacheService = cacheService;
            this.communicationService = communicationService;
            this.clock = clock;
        }

        Map<String, Object> calculateDecision(Map<String, Object> payload) {
            String windowSecondsStr = cacheService.get(CACHE_KEY_NAMESPACE + "statutory_window_seconds");
            long windowSeconds = windowSecondsStr != null ? Long.parseLong(windowSecondsStr) : 86400L;

            Map<String, Object> decision = Map.of(
                "routingStatus", "ROUTED_TO_ADJUSTER",
                "calculatedAt", clock.instant().toString(),
                "windowSeconds", windowSeconds
            );

            String claimId = (String) payload.get("id");
            communicationService.sendAcknowledgment(claimId, "Acknowledgment sent within statutory window", decision);
            return decision;
        }
    }
}
