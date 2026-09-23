package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration mock tests for Claim Data Standardization Decision Validation.
 * Verifies enrichment and routing logic using mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Claim Data Standardization Decision Validation")
class ClaimDataStandardizationDecisionValidationTest {

    @Mock
    private ClaimDataStandardizationDecisionService decisionService;

    private ClaimDataStandardizationDecisionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ClaimDataStandardizationDecisionProcessor(decisionService);
    }

    @Test
    @DisplayName("CatRoutingAppliesCorrectly")
    void cat_routing_applies_correctly() {
        // Arrange
        String claimId = "CLM-2024-CAT-001";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("claimType", "AUTO");
        inputPayload.put("region", "US-EAST");
        inputPayload.put("riskScore", 85);
        inputPayload.put("requiresRouting", true);

        Map<String, Object> expectedPayload = new HashMap<>();
        expectedPayload.put("claimType", "AUTO");
        expectedPayload.put("region", "US-EAST");
        expectedPayload.put("riskScore", 85);
        expectedPayload.put("requiresRouting", true);
        expectedPayload.put("routingStrategy", "CAT_ROUTING");
        expectedPayload.put("decision", "ROUTED_TO_CATALOG");
        expectedPayload.put("enrichedAt", "2024-01-15T10:00:00Z");
        expectedPayload.put("validationStatus", "PASSED");

        when(decisionService.applyEnrichmentAndDecision(any(Map.class)))
                .thenReturn(expectedPayload);

        // Act
        Map<String, Object> resultPayload = processor.standardizeDecision(inputPayload);

        // Assert
        assertNotNull(resultPayload, "Result payload should not be null");
        assertEquals("CAT_ROUTING", resultPayload.get("routingStrategy"), "Routing strategy should be CAT_ROUTING");
        assertEquals("ROUTED_TO_CATALOG", resultPayload.get("decision"), "Decision should indicate routing to catalog");
        assertEquals("PASSED", resultPayload.get("validationStatus"), "Validation status should be PASSED");
    }
}
