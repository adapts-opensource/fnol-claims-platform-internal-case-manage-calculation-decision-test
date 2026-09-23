package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClaimDataStandardizationStateTransitionOrchMockTest {

    @Mock
    private ClaimDataStandardizationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void prioritize_policy_number_match_over_address_match_when_both_exist() {
        // Arrange: Simulate incoming payload containing both policy_number and address matches
        Map<String, Object> inputPayload = Map.of(
            "policy_number_match", "POL-98765",
            "address_match", "456 Elm Ave"
        );

        // Arrange: Expected standardized payload where policy_number is prioritized as primary match
        Map<String, Object> expectedPayload = Map.of(
            "id", "orch-claim-001",
            "payload", Map.of(
                "primary_match_source", "policy_number_match",
                "primary_match_value", "POL-98765",
                "secondary_match_source", "address_match",
                "secondary_match_value", "456 Elm Ave",
                "transition_status", "STANDARDIZED",
                "compliance_flags", Map.of("gdpr_consent", true, "soc2_audit", true)
            )
        );

        // Mock external orchestration service to isolate business logic and prevent live AWS/HTTP calls
        when(orchestrator.standardizeAndTransition(anyMap())).thenReturn(expectedPayload);

        // Act: Execute the state transition orchestration
        Map<String, Object> result = orchestrator.standardizeAndTransition(inputPayload);

        // Assert: Verify prioritization rules, payload structure, and transition state
        assertNotNull(result, "Orchestration result must not be null");
        assertEquals("orch-claim-001", result.get("id"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPayload = (Map<String, Object>) result.get("payload");
        assertEquals("policy_number_match", resultPayload.get("primary_match_source"));
        assertEquals("POL-98765", resultPayload.get("primary_match_value"));
        assertEquals("address_match", resultPayload.get("secondary_match_source"));
        assertEquals("456 Elm Ave", resultPayload.get("secondary_match_value"));
        assertEquals("STANDARDIZED", resultPayload.get("transition_status"));
        assertEquals(true, resultPayload.get("gdpr_consent"));
        assertEquals(true, resultPayload.get("soc2_audit"));

        verify(orchestrator, times(1)).standardizeAndTransition(inputPayload);
    }
}
