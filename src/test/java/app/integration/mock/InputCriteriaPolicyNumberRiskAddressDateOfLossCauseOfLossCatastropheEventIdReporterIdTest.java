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

// JUnit 5 test class with @Test methods
@ExtendWith(MockitoExtension.class)
public class InsuredEngagementStateTransitionTest {

    @Mock
    private DecisionEngine mockDecisionEngine;

    @Mock
    private EngagementStateRepository mockStateRepository;

    private InsuredEngagementService insuredEngagementService;

    @BeforeEach
    void setUp() {
        insuredEngagementService = new InsuredEngagementService(mockDecisionEngine, mockStateRepository);
    }

    @Test
    void input_criteria_policy_number_risk_address_date_of_loss_cause_of_loss_catastrophe_event_id_reporter_id_damaged_area_description_prior_claim_status() {
        // Arrange
        String policyNumber = "POL-123456";
        String riskAddress = "123 Main St, Springfield, IL";
        LocalDate dateOfLoss = LocalDate.of(2023, 10, 15);
        String causeOfLoss = "Fire";
        String catastropheEventId = "CAT-2023-001";
        String reporterId = "REP-789";
        String damagedAreaDescription = "Roof and attic";
        String priorClaimStatus = "Closed";

        Map<String, Object> inputCriteria = Map.of(
            "policy_number", policyNumber,
            "risk_address", riskAddress,
            "date_of_loss", dateOfLoss,
            "cause_of_loss", causeOfLoss,
            "catastrophe_event_id", catastropheEventId,
            "reporter_id", reporterId,
            "damaged_area_description", damagedAreaDescription,
            "prior_claim_status", priorClaimStatus
        );

        String expectedNextState = "UNDER_REVIEW";
        when(mockDecisionEngine.evaluate(inputCriteria)).thenReturn(expectedNextState);
        doNothing().when(mockStateRepository).persist(eq(policyNumber), eq(expectedNextState));

        // Act
        String actualState = insuredEngagementService.transitionState(policyNumber, inputCriteria);

        // Assert
        assertEquals(expectedNextState, actualState);
        verify(mockDecisionEngine, times(1)).evaluate(inputCriteria);
        verify(mockStateRepository, times(1)).persist(policyNumber, expectedNextState);
    }

    // Mock interfaces simulating external decision & persistence layers
    interface DecisionEngine {
        String evaluate(Map<String, Object> criteria);
    }

    interface EngagementStateRepository {
        void persist(String policyNumber, String nextState);
    }

    // Service under test
    static class InsuredEngagementService {
        private final DecisionEngine decisionEngine;
        private final EngagementStateRepository stateRepository;

        InsuredEngagementService(DecisionEngine decisionEngine, EngagementStateRepository stateRepository) {
            this.decisionEngine = decisionEngine;
            this.stateRepository = stateRepository;
        }

        String transitionState(String policyNumber, Map<String, Object> criteria) {
            String nextState = decisionEngine.evaluate(criteria);
            stateRepository.persist(policyNumber, nextState);
            return nextState;
        }
    }
}
