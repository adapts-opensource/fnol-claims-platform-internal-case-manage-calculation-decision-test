package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 mock test class for Insured Engagement & Tracking:orchestration:decision.
 * Verifies policy rewrite handling on the date of loss.
 */
@ExtendWith(MockitoExtension.class)
class PolicyRewrittenOnDateOfLossTest {

    @Mock
    private IncidentService incidentService;
    @Mock
    private PolicyService policyService;
    @Mock
    private DecisionEngine decisionEngine;
    @Mock
    private EngagementTracker engagementTracker;
    @Mock
    private DynamoDBService dynamoDBService;
    @Mock
    private SESService sesService;

    @InjectMocks
    private InsuredEngagementOrchestration orchestrationService;

    @BeforeEach
    void setUp() {
        // Mocks are automatically initialized by MockitoExtension
    }

    @Test
    void policy_rewritten_on_date_of_loss() {
        // Arrange
        String incidentId = "INC-789";
        LocalDate lossDate = LocalDate.of(2023, 11, 20);
        String policyId = "POL-101";

        when(incidentService.getLossDate(incidentId)).thenReturn(lossDate);
        when(policyService.getPolicyRewriteDate(policyId)).thenReturn(lossDate);
        when(policyService.resolvePolicyIdFromIncident(incidentId)).thenReturn(Optional.of(policyId));
        when(decisionEngine.evaluate(any())).thenReturn(DecisionOutcome.ELIGIBLE);
        when(dynamoDBService.putItem(anyString(), anyMap())).thenReturn(true);
        when(sesService.sendEmail(anyString(), anyList(), anyString())).thenReturn("MSG-ID-999");

        // Act
        DecisionOutcome outcome = orchestrationService.processClaimDecision(incidentId);

        // Assert
        assertEquals(DecisionOutcome.ELIGIBLE, outcome);
        verify(engagementTracker).logEngagementEvent(eq(policyId), eq("POLICY_REWRITTEN_ON_LOSS_DATE"), anyString());
        verify(decisionEngine).evaluate(any());
        verify(dynamoDBService).putItem(eq("ReserveLines"), anyMap());
        verify(sesService).sendEmail(eq("claims@newco.insurance"), anyList(), eq("us-east-1"));
    }
}
