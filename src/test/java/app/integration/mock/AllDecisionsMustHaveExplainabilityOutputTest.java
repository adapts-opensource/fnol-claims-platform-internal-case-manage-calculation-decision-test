package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StateTransitionDecisionTest {

    @Mock
    private DecisionStateTransitionService decisionStateTransitionService;

    @Mock
    private ExplainabilityEngine explainabilityEngine;

    @InjectMocks
    private InsuredEngagementService insuredEngagementService;

    @BeforeEach
    void setUp() {
        // Mock initialization handled by MockitoExtension
    }

    @Test
    void all_decisions_must_have_explainability_output() {
        // Arrange
        String claimId = "CLM-98765";
        String decisionState = "APPROVED";
        String explainabilityPayload = "{\"reason_code\":\"COVERAGE_VALID\",\"confidence\":0.95,\"model_version\":\"v1.2\"}";

        DecisionStateResult mockResult = new DecisionStateResult();
        mockResult.setExplainabilityOutput(explainabilityPayload);

        when(decisionStateTransitionService.transition(anyString(), anyString()))
                .thenReturn(mockResult);

        // Act
        DecisionStateResult actualResult = insuredEngagementService.evaluateDecisionState(claimId, decisionState);

        // Assert
        assertNotNull(actualResult, "Decision state transition result must not be null");
        assertNotNull(actualResult.getExplainabilityOutput(), "All decisions must include an explainability output");
    }

    // Mock DTO
    static class DecisionStateResult {
        private String explainabilityOutput;

        public String getExplainabilityOutput() {
            return explainabilityOutput;
        }

        public void setExplainabilityOutput(String explainabilityOutput) {
            this.explainabilityOutput = explainabilityOutput;
        }
    }

    // Mock Service Interfaces
    interface DecisionStateTransitionService {
        DecisionStateResult transition(String claimId, String newState);
    }

    interface ExplainabilityEngine {
        String generate(String decisionId);
    }

    interface InsuredEngagementService {
        DecisionStateResult evaluateDecisionState(String claimId, String decisionState);
    }
}
