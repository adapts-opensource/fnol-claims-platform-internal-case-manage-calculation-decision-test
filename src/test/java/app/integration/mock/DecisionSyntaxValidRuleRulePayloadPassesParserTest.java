package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionSyntaxValidRuleRulePayloadPassesParserTest {

    @Mock
    private DecisionEnrichmentService decisionService;

    private ClaimDataStandardizationEnrichmentProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ClaimDataStandardizationEnrichmentProcessor(decisionService);
    }

    @Test
    void decision_syntax_valid_rule_rule_payload_passes_parser_expected_outcome_proceed_to_approval() {
        // Given
        String payloadId = "PAY-TEST-001";
        Map<String, Object> validRulePayload = Map.of(
            "id", payloadId,
            "payload", Map.of("syntaxCheck", "PASS", "claimType", "AUTO")
        );
        String expectedOutcome = "Proceed to approval";

        when(decisionService.evaluate("Syntax Valid?", validRulePayload))
                .thenReturn(expectedOutcome);

        // When
        String actualOutcome = processor.processEnrichmentDecision(payloadId, validRulePayload);

        // Then
        assertEquals(expectedOutcome, actualOutcome);
        verify(decisionService, times(1)).evaluate("Syntax Valid?", validRulePayload);
    }

    // Mockable interface representing external decision/rules service
    interface DecisionEnrichmentService {
        String evaluate(String decisionName, Map<String, Object> payload);
    }

    // Minimal processor under test
    static class ClaimDataStandardizationEnrichmentProcessor {
        private final DecisionEnrichmentService decisionService;

        ClaimDataStandardizationEnrichmentProcessor(DecisionEnrichmentService decisionService) {
            this.decisionService = decisionService;
        }

        String processEnrichmentDecision(String payloadId, Map<String, Object> payload) {
            return decisionService.evaluate("Syntax Valid?", payload);
        }
    }
}
