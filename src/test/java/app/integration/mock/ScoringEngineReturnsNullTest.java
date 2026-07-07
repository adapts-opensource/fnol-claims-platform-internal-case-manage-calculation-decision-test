package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoringEngineNullIntegrationTest {

    @Mock
    private ScoringEngine scoringEngine;

    @InjectMocks
    private DecisionOrchestrator decisionOrchestrator;

    @Test
    void scoring_engine_returns_null() {
        // Arrange
        Long insuredId = 1001L;
        when(scoringEngine.calculateEngagementScore(insuredId)).thenReturn(null);

        // Act
        String decisionOutcome = decisionOrchestrator.evaluateInsuredEngagement(insuredId);

        // Assert
        assertNotNull(decisionOutcome);
        assertEquals("DEFAULT_FOLLOW_UP", decisionOutcome);
        verify(scoringEngine, times(1)).calculateEngagementScore(insuredId);
        verifyNoMoreInteractions(scoringEngine);
    }
}
