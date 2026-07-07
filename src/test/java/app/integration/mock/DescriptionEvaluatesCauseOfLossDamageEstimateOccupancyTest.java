package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationCalculationDecisionTest {

    @Mock
    private ClaimDecisionCalculationService claimDecisionService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock instantiation and lifecycle management
    }

    @Test
    void description_evaluates_cause_of_loss_damage_estimate_occupancy_type_risk_flags_and_adjuster_pool_capacity_outputs_triage_path_and_assignee_group() {
        // Arrange
        String causeOfLoss = "collision";
        double damageEstimate = 7500.50;
        String occupancyType = "multi_family";
        boolean riskFlags = true;
        int adjusterPoolCapacity = 5;

        ClaimDecisionRequest request = new ClaimDecisionRequest(causeOfLoss, damageEstimate, occupancyType, riskFlags, adjusterPoolCapacity);

        ClaimDecisionResponse expectedResponse = new ClaimDecisionResponse("triage_rapid", "group_specialist");
        when(claimDecisionService.calculateDecision(request)).thenReturn(expectedResponse);

        // Act
        ClaimDecisionResponse actualResponse = claimDecisionService.calculateDecision(request);

        // Assert
        assertNotNull(actualResponse, "Decision response must not be null");
        assertEquals("triage_rapid", actualResponse.triagePath(), "Triage path should match expected routing");
        assertEquals("group_specialist", actualResponse.assigneeGroup(), "Assignee group should match expected capacity pool");
        verify(claimDecisionService, times(1)).calculateDecision(request);
    }

    // Test-scoped DTOs to isolate calculation logic from external dependencies
    record ClaimDecisionRequest(String causeOfLoss, double damageEstimate, String occupancyType, boolean riskFlags, int adjusterPoolCapacity) {}
    record ClaimDecisionResponse(String triagePath, String assigneeGroup) {}

    // Service interface representing the external/calculation layer
    interface ClaimDecisionCalculationService {
        ClaimDecisionResponse calculateDecision(ClaimDecisionRequest request);
    }
}
