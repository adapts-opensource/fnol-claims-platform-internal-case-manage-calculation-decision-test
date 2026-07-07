package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiFastTrackDecisionTest {

    @Mock
    private StateTransitionCalculator stateCalculator;

    @Mock
    private ClaimNumberGenerator claimNumberGenerator;

    @Mock
    private TaskOrchestrator taskOrchestrator;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private FnoLOrchestrator fnoLOrchestrator;

    private Map<String, Object> mockInput;

    @BeforeEach
    void setUp() {
        mockInput = Map.of(
                "channel", "API",
                "product", "HO3",
                "causeOfLoss", "Wind",
                "severity", "Low",
                "attorneyFlag", false,
                "paFlag", false,
                "aobFlag", false,
                "policyStatus", "Active",
                "documentationSufficient", true
        );
    }

    @Test
    void orchestrate_api_fast_track_fnol_decision() {
        // Arrange
        String expectedClaimNumber = "CLM-FL01-2024-1042";
        String expectedState = "Claim Opened";
        String expectedClaimType = "Fast-track claim";
        List<String> expectedTasks = List.of("Review FNOL", "Assign Adjuster");
        String userId = "api_user_01";

        when(stateCalculator.calculate(anyMap())).thenReturn(expectedState);
        when(claimNumberGenerator.generate(anyString(), anyString())).thenReturn(expectedClaimNumber);

        // Act
        FnoLOrchestrationResult result = fnoLOrchestrator.orchestrate(mockInput, userId);

        // Assert State & Type
        assertEquals(expectedState, result.getState());
        assertEquals(expectedClaimType, result.getClaimType());

        // Assert Claim Number Format
        assertNotNull(result.getClaimNumber());
        assertTrue(result.getClaimNumber().matches("CLM-FL01-\\d{4}-\\d{4}"));

        // Assert Tasks Created
        verify(taskOrchestrator).initializeTasks(eq(expectedClaimNumber), eq(expectedTasks));

        // Assert Audit Log Records Channel & User Actor
        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> actorCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).recordEvent(anyString(), channelCaptor.capture(), actorCaptor.capture(), anyString());
        assertEquals("API", channelCaptor.getValue());
        assertEquals(userId, actorCaptor.getValue());
    }

    /**
     * Minimal DTO to represent orchestration output for test compilation.
     * In production, this would reside in app.domain.model.
     */
    record FnoLOrchestrationResult(String claimNumber, String state, String claimType) {}
}
