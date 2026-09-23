package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CatastropheEventIdNotYetIngestedTest {

    @Mock
    private CatastropheEventIngestionService ingestionService;

    @Mock
    private OrchestrationDecisionEngine decisionEngine;

    @InjectMocks
    private InsuredEngagementOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection and stubbing lifecycle
    }

    @Test
    void catastrophe_event_id_not_yet_ingested() {
        String claimId = "CLM-1001";
        String catEventId = "CAT-EVT-7788";

        // Arrange: Mock ingestion service to indicate event is not yet ingested
        when(ingestionService.isEventIngested(catEventId)).thenReturn(false);
        when(ingestionService.getEventStatus(catEventId)).thenReturn("PENDING_INGESTION");

        // Arrange: Mock decision engine to return deferral state when status is pending
        when(decisionEngine.evaluateDecision(claimId, "PENDING_INGESTION"))
                .thenReturn(OrchestrationOutcome.WAIT_FOR_CATASTROPHE_EVENT);

        // Act & Assert: Verify orchestrator handles missing ingestion gracefully without throwing
        assertDoesNotThrow(() -> {
            OrchestrationOutcome outcome = orchestrator.makeEngagementDecision(claimId, catEventId);
            assertEquals(OrchestrationOutcome.WAIT_FOR_CATASTROPHE_EVENT, outcome);
        });

        // Verify: Ensure correct service interactions occurred with proper validation
        verify(ingestionService, times(1)).isEventIngested(catEventId);
        verify(ingestionService, times(1)).getEventStatus(catEventId);
        verify(decisionEngine, times(1)).evaluateDecision(claimId, "PENDING_INGESTION");
    }
}
