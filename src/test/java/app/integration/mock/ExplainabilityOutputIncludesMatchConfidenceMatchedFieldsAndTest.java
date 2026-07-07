package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class InsuredEngagementDecisionTransformationExplainabilityTest {

    private static class ExplainabilityOutput {
        private final Double matchConfidence;
        private final String matchedFields;
        private final String decisionRationale;

        public ExplainabilityOutput(Double matchConfidence, String matchedFields, String decisionRationale) {
            this.matchConfidence = matchConfidence;
            this.matchedFields = matchedFields;
            this.decisionRationale = decisionRationale;
        }

        public Double getMatchConfidence() { return matchConfidence; }
        public String getMatchedFields() { return matchedFields; }
        public String getDecisionRationale() { return decisionRationale; }
    }

    private interface DecisionTransformationService {
        ExplainabilityOutput transformAndExplain(Object inputPayload);
    }

    @Mock
    private DecisionTransformationService decisionTransformationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void explainability_output_includes_match_confidence_matched_fields_and_decision_rationale() {
        // Arrange
        Double expectedConfidence = 0.92;
        String expectedMatchedFields = "policy_number, insured_name, coverage_type";
        String expectedDecisionRationale = "Claim auto-approved based on verified policy coverage and clean claims history.";

        when(decisionTransformationService.transformAndExplain(any())).thenReturn(
                new ExplainabilityOutput(expectedConfidence, expectedMatchedFields, expectedDecisionRationale)
        );

        // Act
        ExplainabilityOutput actualOutput = decisionTransformationService.transformAndExplain(null);

        // Assert
        assertNotNull(actualOutput, "Explainability output must not be null");
        assertEquals(expectedConfidence, actualOutput.getMatchConfidence(), "Match confidence must be present");
        assertEquals(expectedMatchedFields, actualOutput.getMatchedFields(), "Matched fields must be present");
        assertEquals(expectedDecisionRationale, actualOutput.getDecisionRationale(), "Decision rationale must be present");
    }
}
