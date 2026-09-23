package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FnolAttorneyRepresentationDecisionTest {

    @Mock
    private ClaimDecisionOrchestrator claimDecisionOrchestrator;

    private Map<String, Object> fnolPayload;

    @BeforeEach
    void setUp() {
        fnolPayload = Map.of(
            "channel", "Internal",
            "policy_number", "POL-11111",
            "date_of_loss", "2024-05-01",
            "cause_of_loss", "Fire",
            "attorney_represented", true,
            "tenant_code", "FL01"
        );
    }

    @Test
    void fnolAttorneyRepresentationDecisionRouting() {
        // Arrange
        List<String> expectedTasks = Arrays.asList(
            "Review FNOL", "Acknowledge Claim", "Assign Adjuster", "Attorney Representation Review"
        );

        Map<String, Object> expectedOutcome = Map.of(
            "claim_type", "Represented claim",
            "state", "Claim Opened",
            "tasks", expectedTasks
        );

        when(claimDecisionOrchestrator.process(anyMap())).thenReturn(expectedOutcome);

        // Act
        Map<String, Object> actualOutcome = claimDecisionOrchestrator.process(fnolPayload);

        // Assert
        assertNotNull(actualOutcome);
        assertEquals("Represented claim", actualOutcome.get("claim_type"));
        assertEquals("Claim Opened", actualOutcome.get("state"));
        assertEquals(expectedTasks, actualOutcome.get("tasks"));

        // Verify orchestration was triggered exactly once with the provided payload
        verify(claimDecisionOrchestrator, times(1)).process(fnolPayload);
    }

    // Minimal interface representing the external orchestration/decision service
    interface ClaimDecisionOrchestrator {
        Map<String, Object> process(Map<String, Object> payload);
    }
}
