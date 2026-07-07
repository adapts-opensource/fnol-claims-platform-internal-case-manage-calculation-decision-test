package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExplainabilityOutputIncludesRuleIdsConfidenceScoreAndDataVersionTest {

    @Mock
    private ClaimEnrichmentProvider enrichmentProvider;

    @InjectMocks
    private ClaimStandardizationProcessor processor;

    @BeforeEach
    void setUp() {
        // Reset mocks and prepare test fixtures before each test
        lenient().when(enrichmentProvider.provideExplainability(anyMap())).thenReturn(Map.of());
    }

    @Test
    void explainability_output_includes_rule_ids_confidence_score_and_data_version() {
        // Arrange
        Map<String, Object> mockExplainability = Map.of(
                "rule_ids", List.of("DECISION_RULE_101", "DECISION_RULE_102"),
                "confidence_score", 0.89,
                "data_version", "v1.0"
        );

        when(enrichmentProvider.provideExplainability(anyMap())).thenReturn(mockExplainability);

        // Act
        Map<String, Object> result = processor.enrichDecision(Map.of("id", "claim-456", "payload", Map.of("type", "AUTO")));

        // Assert
        Map<String, Object> explainability = (Map<String, Object>) result.get("explainability");
        assertNotNull(explainability, "Explainability output must not be null");

        assertTrue(explainability.containsKey("rule_ids"), "Explainability output must include rule_ids");
        assertTrue(explainability.containsKey("confidence_score"), "Explainability output must include confidence_score");
        assertTrue(explainability.containsKey("data_version"), "Explainability output must include data_version");

        assertEquals(List.of("DECISION_RULE_101", "DECISION_RULE_102"), explainability.get("rule_ids"));
        assertEquals(0.89, explainability.get("confidence_score"));
        assertEquals("v1.0", explainability.get("data_version"));
    }
}
