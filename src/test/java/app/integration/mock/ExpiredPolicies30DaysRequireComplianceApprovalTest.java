package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationRoutingDecisionCalculationTest {

    @Mock
    private PolicyDataStore policyDataStore;

    @Mock
    private RoutingDecisionCalculator decisionCalculator;

    private ClaimInitiationService claimInitiationService;

    @BeforeEach
    void setUp() {
        claimInitiationService = new ClaimInitiationService(policyDataStore, decisionCalculator);
    }

    @Test
    void expired_policies_30_days_require_compliance_approval() {
        // Arrange
        String policyId = "POL-EXPIRED-30D";
        LocalDate expirationDate = LocalDate.now().minusDays(31);
        Map<String, Object> claimPayload = Map.of("policyId", policyId, "claimId", "CLM-100");

        when(policyDataStore.getPolicyDetails(policyId))
                .thenReturn(Map.of("expirationDate", expirationDate, "status", "EXPIRED"));

        when(decisionCalculator.evaluateClaimDecision(policyId, expirationDate))
                .thenReturn(Map.of("requiresComplianceApproval", true, "routingStage", "COMPLIANCE_REVIEW"));

        // Act
        Map<String, Object> decisionResult = claimInitiationService.calculateRoutingDecision(claimPayload);

        // Assert
        assertNotNull(decisionResult);
        assertTrue((Boolean) decisionResult.get("requiresComplianceApproval"),
                "Expired policies > 30 days must require compliance approval");
        assertEquals("COMPLIANCE_REVIEW", decisionResult.get("routingStage"));
    }
}
