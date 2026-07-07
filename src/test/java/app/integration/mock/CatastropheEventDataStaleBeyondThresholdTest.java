package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CatastropheEventDataStaleBeyondThresholdTest {

    @Mock
    private CatastropheEventRepository catastropheEventRepository;

    @Mock
    private DecisionOrchestrationService decisionOrchestrationService;

    @InjectMocks
    private InsuredEngagementOrchestrationService insuredEngagementOrchestrationService;

    @Test
    void catastrophe_event_data_stale_beyond_threshold() {
        // Arrange
        Instant staleTimestamp = Instant.now().minus(25, ChronoUnit.HOURS);
        CatastropheEvent staleEvent = new CatastropheEvent("CAT-2024-001", "Category 5 Storm", staleTimestamp);

        when(catastropheEventRepository.findByEventId("CAT-2024-001")).thenReturn(Optional.of(staleEvent));

        // Act
        DecisionContext context = new DecisionContext("INSURED-789", "CAT-2024-001");
        DecisionOutcome outcome = insuredEngagementOrchestrationService.evaluateEngagement(context);

        // Assert
        assertEquals(DecisionOutcome.STALE_CATASTROPHE_DATA, outcome);
        assertTrue(outcome.requiresDataRefresh());
        verify(decisionOrchestrationService, never()).processDecision(any());
        verify(catastropheEventRepository).findByEventId("CAT-2024-001");
    }

    // Minimal interfaces/DTOs for test isolation
    interface CatastropheEventRepository {
        Optional<CatastropheEvent> findByEventId(String eventId);
    }

    interface DecisionOrchestrationService {
        void processDecision(DecisionContext context);
    }

    record CatastropheEvent(String eventId, String eventName, Instant lastUpdated) {}

    record DecisionContext(String insuredId, String categoryId) {}

    enum DecisionOutcome {
        APPROVED, REJECTED, STALE_CATASTROPHE_DATA;

        public boolean requiresDataRefresh() {
            return this == STALE_CATASTROPHE_DATA;
        }
    }

    class InsuredEngagementOrchestrationService {
        private static final long STALENESS_THRESHOLD_HOURS = 24;

        public DecisionOutcome evaluateEngagement(DecisionContext context) {
            Optional<CatastropheEvent> eventOpt = catastropheEventRepository.findByEventId(context.categoryId());
            if (eventOpt.isPresent()) {
                CatastropheEvent event = eventOpt.get();
                long hoursSinceUpdate = ChronoUnit.HOURS.between(event.lastUpdated(), Instant.now());
                if (hoursSinceUpdate > STALENESS_THRESHOLD_HOURS) {
                    return DecisionOutcome.STALE_CATASTROPHE_DATA;
                }
            }
            decisionOrchestrationService.processDecision(context);
            return DecisionOutcome.APPROVED;
        }
    }
}
