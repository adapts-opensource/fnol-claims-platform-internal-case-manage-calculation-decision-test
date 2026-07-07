package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ClaimInitiationRoutingOrchestrationDecisionTest {

    @Mock
    private ClaimDecisionOrchestrator orchestrator;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ClaimStatusRepository statusRepository;

    @Mock
    private ClaimDataValidator dataValidator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void outputCriteriaSuccessOutputsDolValidityStatusCoveragePeriodConfirmedRestrictionsFlaggedFailureOutputsValidationError() {
        // Given: Input payload simulating a valid claim with DOL, coverage period, restrictions, and no moratorium
        String claimId = "CLM-2024-001";
        Map<String, Object> inputPayload = Map.of(
            "dol", "2023-11-10",
            "coveragePeriod", Map.of("startDate", "2023-01-01", "endDate", "2024-12-31"),
            "restrictions", List.of("geographic_limit"),
            "moratoriumFlag", false
        );

        // Setup mocks to simulate successful routing decision path
        when(orchestrator.evaluateDecision(anyString(), anyMap())).thenReturn(Map.of(
            "successOutputs", List.of("dol_validity_status", "coverage_period_confirmed", "restrictions_flagged"),
            "failureOutputs", List.of(),
            "statusUpdates", List.of(Map.of("DOL_VALIDATION", "PASSED")),
            "userVisibleOutput", "Confirmation of coverage period."
        ));
        doNothing().when(eventPublisher).publishEvent(eq("dol.validated"), anyString());
        doNothing().when(statusRepository).updateClaimStatus(eq(claimId), eq("DOL_VALIDATION"), eq("PASSED"));

        // When: Orchestrator processes the claim initiation
        Map<String, Object> result = orchestrator.evaluateDecision(claimId, inputPayload);

        // Then: Verify success outputs
        assertNotNull(result);
        List<String> successOutputs = (List<String>) result.get("successOutputs");
        assertTrue(successOutputs.contains("dol_validity_status"), "DOL validity status should be confirmed");
        assertTrue(successOutputs.contains("coverage_period_confirmed"), "Coverage period should be confirmed");
        assertTrue(successOutputs.contains("restrictions_flagged"), "Restrictions should be flagged");

        // Verify failure outputs are absent for this successful path
        List<String> failureOutputs = (List<String>) result.get("failureOutputs");
        assertNotNull(failureOutputs);
        assertFalse(failureOutputs.contains("validation_error"), "Should not contain validation_error on success");
        assertFalse(failureOutputs.contains("moratorium_conflict"), "Should not contain moratorium_conflict on success");

        // Verify status updates
        List<Map<String, String>> statusUpdates = (List<Map<String, String>>) result.get("statusUpdates");
        assertTrue(statusUpdates.stream().anyMatch(u -> "PASSED".equals(u.get("DOL_VALIDATION"))),
                "DOL validation status should be updated to PASSED");

        // Verify emitted events
        verify(eventPublisher, times(1)).publishEvent(eq("dol.validated"), eq(claimId));

        // Verify user visible outputs
        assertEquals("Confirmation of coverage period.", result.get("userVisibleOutput"),
                "User should see confirmation of coverage period");

        // Additional verification for infrastructure I/O contracts (mocked)
        verify(statusRepository, times(1)).updateClaimStatus(eq(claimId), eq("DOL_VALIDATION"), eq("PASSED"));
    }
}
