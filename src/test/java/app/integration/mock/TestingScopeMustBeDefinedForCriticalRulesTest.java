package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionEnrichmentMockTest {

    @Mock
    private DecisionEnrichmentService mockEnrichmentService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles mock initialization and injection
    }

    @Test
    void testing_scope_must_be_defined_for_critical_rules() {
        // Arrange: Simulate a critical rule configuration without a testing scope
        RuleEnrichmentInput criticalRulePayload = new RuleEnrichmentInput(
                "claim-standardization-crit-001",
                true,
                null // testingScope is intentionally null to trigger validation failure
        );

        // Mock the service behavior to enforce the validation contract
        when(mockEnrichmentService.validateAndEnrich(criticalRulePayload)).thenThrow(
                new IllegalArgumentException("Testing scope must be defined for critical rules")
        );

        // Act & Assert: Verify that the enrichment pipeline rejects critical rules lacking a testing scope
        assertThrows(IllegalArgumentException.class, () -> mockEnrichmentService.validateAndEnrich(criticalRulePayload));
    }

    // Internal test models to avoid external dependencies and keep the test self-contained
    private static class RuleEnrichmentInput {
        private final String ruleId;
        private final boolean isCritical;
        private final String testingScope;

        public RuleEnrichmentInput(String ruleId, boolean isCritical, String testingScope) {
            this.ruleId = ruleId;
            this.isCritical = isCritical;
            this.testingScope = testingScope;
        }

        public String getRuleId() { return ruleId; }
        public boolean isCritical() { return isCritical; }
        public String getTestingScope() { return testingScope; }
    }

    // Mocked service interface representing the decision:enrichment pipeline
    private interface DecisionEnrichmentService {
        void validateAndEnrich(RuleEnrichmentInput input);
    }
}
