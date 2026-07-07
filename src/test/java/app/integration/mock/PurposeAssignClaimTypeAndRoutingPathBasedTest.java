package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

// Minimal domain models and interfaces for test context
record LossCharacteristics(String damageType, double estimatedLoss, String jurisdiction) {}
record ClaimDecision(String claimType, String routingPath) {}

interface LossDataGateway {
    LossCharacteristics fetchLossDetails(String caseId);
}

interface RoutingPolicyService {
    String determineRoutingPath(String claimType, double estimatedLoss);
}

class ClaimDecisionCalculator {
    private final LossDataGateway lossDataGateway;
    private final RoutingPolicyService routingPolicyService;

    ClaimDecisionCalculator(LossDataGateway lossDataGateway, RoutingPolicyService routingPolicyService) {
        this.lossDataGateway = lossDataGateway;
        this.routingPolicyService = routingPolicyService;
    }

    ClaimDecision calculateDecision(String caseId) {
        LossCharacteristics characteristics = lossDataGateway.fetchLossDetails(caseId);
        String claimType = determineClaimType(characteristics.damageType());
        String routingPath = routingPolicyService.determineRoutingPath(claimType, characteristics.estimatedLoss());
        return new ClaimDecision(claimType, routingPath);
    }

    private String determineClaimType(String damageType) {
        if ("COLLISION".equalsIgnoreCase(damageType) || "COMPREHENSIVE".equalsIgnoreCase(damageType)) {
            return "AUTO_PHYSICAL_DAMAGE";
        }
        return "GENERAL_CLAIM";
    }
}

/**
 * JUnit 5 test class with @Test methods
 */
public class InternalCaseManagementCalculationDecisionTest {

    @Mock
    private LossDataGateway lossDataGateway;

    @Mock
    private RoutingPolicyService routingPolicyService;

    private ClaimDecisionCalculator decisionCalculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        decisionCalculator = new ClaimDecisionCalculator(lossDataGateway, routingPolicyService);
    }

    @Test
    void purpose_assign_claim_type_and_routing_path_based_on_loss_characteristics() {
        // Arrange
        String caseId = "CASE-INTERNAL-001";
        LossCharacteristics lossDetails = new LossCharacteristics("COLLISION", 18500.0, "CA");
        when(lossDataGateway.fetchLossDetails(caseId)).thenReturn(lossDetails);
        when(routingPolicyService.determineRoutingPath("AUTO_PHYSICAL_DAMAGE", 18500.0)).thenReturn("SEVERITY_HIGH_FAST_TRACK");

        // Act
        ClaimDecision decision = decisionCalculator.calculateDecision(caseId);

        // Assert
        assertNotNull(decision);
        assertEquals("AUTO_PHYSICAL_DAMAGE", decision.claimType());
        assertEquals("SEVERITY_HIGH_FAST_TRACK", decision.routingPath());
        verify(lossDataGateway, times(1)).fetchLossDetails(caseId);
        verify(routingPolicyService, times(1)).determineRoutingPath("AUTO_PHYSICAL_DAMAGE", 18500.0);
    }
}
