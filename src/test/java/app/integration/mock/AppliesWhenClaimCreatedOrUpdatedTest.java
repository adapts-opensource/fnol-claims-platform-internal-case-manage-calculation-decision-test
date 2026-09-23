package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementTrackingDecisionOrchestrationTest {

    @Mock
    private ClaimService claimService;

    @Mock
    private DecisionOrchestrator decisionOrchestrator;

    private InsuredEngagementTrackingOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new InsuredEngagementTrackingOrchestrationService(claimService, decisionOrchestrator);
    }

    @Test
    void applies_when_claim_created_or_updated() {
        // Arrange
        Claim claim = new Claim();
        claim.setClaimId("CLM-12345");
        claim.setEventType(ClaimEventType.CREATED);

        // Act
        orchestrationService.evaluateEngagement(claim);

        // Assert
        verify(decisionOrchestrator, times(1)).applyDecision(claim);
        verifyNoMoreInteractions(decisionOrchestrator);
    }
}
