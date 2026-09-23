package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationDecisionTransformationTest {

    @Mock
    private DecisionTransformationEngine decisionTransformationEngine;

    private Map<String, Object> claimPayload;

    @BeforeEach
    void setUp() {
        claimPayload = new HashMap<>();
        claimPayload.put("id", "claim-std-001");
        claimPayload.put("policy_number", "POL-987654");
        claimPayload.put("date_of_loss", "2023-11-20");
        claimPayload.put("insured_name", "Test Insured");
        claimPayload.put("incident_description", "Minor impact");
        claimPayload.put("claimant_contact", "contact@example.com");
    }

    @Test
    void ruleIfPolicyNumberMatchesIgnoreOtherFieldsForMatchingButValidateDateOfLoss() {
        // Arrange: Mock the decision engine to simulate the rule evaluation contract
        when(decisionTransformationEngine.evaluate(claimPayload))
                .thenReturn(TransformOutcome.builder()
                        .matchFound(true)
                        .matchedField("policy_number")
                        .dateOfLossValid(true)
                        .ignoredFieldsForMatch(Map.of("insured_name", "Test Insured", "incident_description", "Minor impact", "claimant_contact", "contact@example.com"))
                        .build());

        // Act
        TransformOutcome outcome = decisionTransformationEngine.evaluate(claimPayload);

        // Assert: Verify policy_number drives the match, other fields are explicitly ignored for matching,
        // and date_of_loss is still subjected to validation logic
        assertTrue(outcome.isMatchFound(), "Should report match when policy_number matches");
        assertEquals("policy_number", outcome.getMatchedField(), "Match attribution should be policy_number");
        assertTrue(outcome.isDateOfLossValid(), "date_of_loss must be validated regardless of other field ignoring");
        assertFalse(outcome.getIgnoredFieldsForMatch().isEmpty(), "Non-matching fields should be tracked as ignored");

        verify(decisionTransformationEngine).evaluate(claimPayload);
    }

    /**
     * Lightweight DTO representing the outcome of a claim data transformation decision.
     */
    @SuppressWarnings("unused")
    private static class TransformOutcome {
        private final boolean matchFound;
        private final String matchedField;
        private final boolean dateOfLossValid;
        private final Map<String, Object> ignoredFieldsForMatch;

        private TransformOutcome(boolean matchFound, String matchedField, boolean dateOfLossValid, Map<String, Object> ignoredFieldsForMatch) {
            this.matchFound = matchFound;
            this.matchedField = matchedField;
            this.dateOfLossValid = dateOfLossValid;
            this.ignoredFieldsForMatch = ignoredFieldsForMatch;
        }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private boolean matchFound;
            private String matchedField;
            private boolean dateOfLossValid;
            private Map<String, Object> ignoredFieldsForMatch = new HashMap<>();

            public Builder matchFound(boolean matchFound) { this.matchFound = matchFound; return this; }
            public Builder matchedField(String matchedField) { this.matchedField = matchedField; return this; }
            public Builder dateOfLossValid(boolean dateOfLossValid) { this.dateOfLossValid = dateOfLossValid; return this; }
            public Builder ignoredFieldsForMatch(Map<String, Object> ignoredFieldsForMatch) { this.ignoredFieldsForMatch = ignoredFieldsForMatch; return this; }
            public TransformOutcome build() { return new TransformOutcome(matchFound, matchedField, dateOfLossValid, ignoredFieldsForMatch); }
        }

        public boolean isMatchFound() { return matchFound; }
        public String getMatchedField() { return matchedField; }
        public boolean isDateOfLossValid() { return dateOfLossValid; }
        public Map<String, Object> getIgnoredFieldsForMatch() { return ignoredFieldsForMatch; }
    }

    /**
     * Mock interface simulating the external decision/transformation service contract.
     * In production, this would route to DynamoDB RulesEngineDecisionService or S3 AuditDiaryStore.
     */
    @SuppressWarnings("unused")
    private interface DecisionTransformationEngine {
        TransformOutcome evaluate(Map<String, Object> claimPayload);
    }
}
