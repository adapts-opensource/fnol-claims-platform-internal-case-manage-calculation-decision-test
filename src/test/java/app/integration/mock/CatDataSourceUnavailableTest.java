package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatDataSourceUnavailableTest {

    @Mock
    private CatDataSource catDataSource;

    private DecisionOrchestrator decisionOrchestrator;

    @BeforeEach
    void setUp() {
        decisionOrchestrator = new DecisionOrchestrator(catDataSource);
    }

    @Test
    void cat_data_source_unavailable() {
        // Arrange
        String id = "CLM-9921";
        Map<String, Object> payload = new HashMap<>();
        payload.put("claimType", "AUTO_COLLISION");
        payload.put("estimatedAmount", 4200.50);
        payload.put("policyNumber", "POL-8834");

        // Simulate Cat data source unavailability
        when(catDataSource.fetchRoutingRules(eq(id), anyMap())).thenThrow(new RuntimeException("CAT_DATA_SOURCE_UNAVAILABLE"));

        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            decisionOrchestrator.evaluateDecision(id, payload);
        });

        assertTrue(thrown.getMessage().contains("CAT_DATA_SOURCE_UNAVAILABLE"), 
            "Expected orchestration to surface Cat data source unavailability");
        verify(catDataSource, times(1)).fetchRoutingRules(eq(id), eq(payload));
    }
}
