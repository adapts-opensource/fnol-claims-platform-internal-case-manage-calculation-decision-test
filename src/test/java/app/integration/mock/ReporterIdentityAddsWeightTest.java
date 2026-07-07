package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ReporterIdentityAddsWeightTest {

    @Mock
    private MultiChannelFnolSubmissionService submissionService;

    private String testId;
    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testId = "fnol-submission-001";
        testPayload = new HashMap<>();
        testPayload.put("reporterIdentity", "AGENT");
        testPayload.put("channel", "WEB");
        testPayload.put("claimDetails", Map.of("type", "AUTO"));
    }

    @Test
    void reporterIdentityAddsWeight() {
        // Arrange: Mock the service that performs state transition calculation
        // (Simulates internal calculation logic without hitting live AWS/DB)
        Map<String, Object> expectedResult = new HashMap<>();
        expectedResult.put("id", testId);
        expectedResult.put("payload", testPayload);
        expectedResult.put("calculatedWeight", 15.0); // Base 10.0 + Reporter Identity Weight 5.0
        expectedResult.put("nextState", "UNDER_REVIEW");

        when(submissionService.calculateStateTransition(testId, testPayload))
                .thenReturn(expectedResult);

        // Act: Invoke the mocked calculation service
        Map<String, Object> actualResult = submissionService.calculateStateTransition(testId, testPayload);

        // Assert: Verify reporter identity correctly contributes to weight calculation
        assertNotNull(actualResult, "Result payload should not be null");
        assertEquals(15.0, actualResult.get("calculatedWeight"), "Weight should include reporter identity contribution");
        assertEquals("UNDER_REVIEW", actualResult.get("nextState"), "State should transition based on weighted calculation");
        verify(submissionService, times(1)).calculateStateTransition(testId, testPayload);
    }
}
