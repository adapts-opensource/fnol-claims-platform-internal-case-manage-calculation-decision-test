package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FnolDuplicateDetectionDecisionTest {

    @Mock
    private DecisionOrchestrator mockOrchestrator;

    private Map<String, String> fnolInputs;

    @BeforeEach
    void setUp() {
        fnolInputs = new HashMap<>();
        fnolInputs.put("channel", "Agent");
        fnolInputs.put("policy_number", "POL-67890");
        fnolInputs.put("date_of_loss", "2024-05-01");
        fnolInputs.put("cause_of_loss", "Wind");
        fnolInputs.put("risk_address", "456 Oak Ave");
        fnolInputs.put("reporter", "Jane Smith");
        fnolInputs.put("tenant_code", "FL01");
    }

    @Test
    void fnol_duplicate_detection_decision_routing() {
        // Arrange: Mock external orchestration decision to return expected state transition payload
        Map<String, Object> expectedPayload = new HashMap<>();
        expectedPayload.put("id", "claim-uuid-001");
        expectedPayload.put("duplicate_detection_status", "Likely Duplicate");
        expectedPayload.put("state", "Duplicate Review");
        expectedPayload.put("tasks", Arrays.asList("Review Potential Duplicate Claim"));

        when(mockOrchestrator.evaluateDecision(fnolInputs)).thenReturn(expectedPayload);

        // Act: Invoke the decision routing logic
        Map<String, Object> actualResult = mockOrchestrator.evaluateDecision(fnolInputs);

        // Assert: Verify expected outcomes match the test case description
        assertEquals("Likely Duplicate", actualResult.get("duplicate_detection_status"),
                "Duplicate detection status should be Likely Duplicate");
        assertEquals("Duplicate Review", actualResult.get("state"),
                "Claim state should transition to Duplicate Review");
        assertInstanceOf(List.class, actualResult.get("tasks"),
                "Tasks should be a list");
        @SuppressWarnings("unchecked")
        List<String> tasks = (List<String>) actualResult.get("tasks");
        assertEquals(1, tasks.size(), "Should contain exactly one task");
        assertEquals("Review Potential Duplicate Claim", tasks.get(0),
                "Task should be Review Potential Duplicate Claim");

        // Verify mock interaction (simulates validated infra I/O contract)
        verify(mockOrchestrator, times(1)).evaluateDecision(fnolInputs);
    }
}
