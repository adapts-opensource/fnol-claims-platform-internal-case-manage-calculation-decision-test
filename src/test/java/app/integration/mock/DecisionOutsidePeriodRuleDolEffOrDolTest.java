package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionValidationTest {

    @Mock
    private DecisionValidationService decisionValidationService;

    @Mock
    private RoutingService routingService;

    @Mock
    private CacheService cacheService;

    @Mock
    private CommunicationService communicationService;

    private ClaimInitiationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ClaimInitiationOrchestrator(decisionValidationService, routingService, cacheService, communicationService);
    }

    @Test
    void decision_outside_period_rule_dol_eff_or_dol_exp_and_no_reinstatement_expected_outcome_route_to_coverage_review_capture_claim() {
        // Arrange: DOL < eff, no reinstatement -> Outside Period decision
        String claimId = "CLM-DOL-OUTSIDE-001";
        Map<String, Object> payload = Map.of(
                "dateOfLoss", "2023-01-15",
                "effectiveDate", "2023-06-01",
                "expiryDate", "2023-12-31",
                "reinstatement", false
        );

        when(decisionValidationService.validateClaimDecision(claimId, payload)).thenReturn("OUTSIDE_PERIOD");
        when(routingService.routeToCoverageReview(claimId)).thenReturn(true);
        when(cacheService.put(anyString(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(communicationService.sendAcknowledgment(anyString(), anyList(), anyString())).thenReturn("SES-MSG-ID-123");

        // Act
        boolean result = orchestrator.processClaimInitiation(claimId, payload);

        // Assert
        assertTrue(result, "Claim should be captured and routed successfully");
        verify(decisionValidationService).validateClaimDecision(claimId, payload);
        verify(routingService).routeToCoverageReview(claimId);
        verify(cacheService).put("Cache & Reference Data:cache:", claimId, "OUTSIDE_PERIOD", 3600);
        verify(communicationService).sendAcknowledgment("claims@newco.insurance", List.of("applicant@newco.com"), "us-east-1");
    }
}
