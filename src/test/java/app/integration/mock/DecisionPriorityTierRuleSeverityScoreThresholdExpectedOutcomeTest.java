package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DecisionPriorityTierRuleSeverityScoreThresholdTest {

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private ClaimRoutingCalculator routingCalculator;

    @InjectMocks
    private RoutingDecisionEngine routingDecisionEngine;

    @BeforeEach
    void setUp() {
        // Initialization for external I/O mocks is handled via @Mock and @InjectMocks
    }

    @Test
    void decision_priority_tier_rule_severity_score_threshold_expected_outcome_sla_adjustment() {
        // Arrange
        String claimId = "CLM-2024-001";
        Map<String, Object> payload = Map.of(
                "severityScore", 85,
                "claimType", "AUTO",
                "region", "US-EAST"
        );

        // Mock external reference data (simulating Redis/DynamoDB threshold lookup)
        double severityThreshold = 80.0;
        when(referenceDataService.getThreshold("AUTO", "US-EAST", "SEVERITY")).thenReturn(severityThreshold);

        // Mock calculation outcome
        Map<String, Object> expectedDecision = Map.of(
                "id", claimId,
                "payload", payload,
                "decision", "Priority tier",
                "rule", "Severity score threshold",
                "expected_outcome", "SLA adjustment",
                "priorityTier", "HIGH",
                "slaStatus", "ADJUSTED"
        );

        when(routingCalculator.calculate(payload)).thenReturn(expectedDecision);

        // Act
        Map<String, Object> actualDecision = routingDecisionEngine.processClaimInitiation(claimId, payload);

        // Assert
        assertNotNull(actualDecision);
        assertEquals("Priority tier", actualDecision.get("decision"));
        assertEquals("Severity score threshold", actualDecision.get("rule"));
        assertEquals("SLA adjustment", actualDecision.get("expected_outcome"));
        assertEquals("HIGH", actualDecision.get("priorityTier"));
        assertEquals("ADJUSTED", actualDecision.get("slaStatus"));
    }
}

// Supporting interfaces and implementation for compilation and mock isolation
interface ReferenceDataService {
    double getThreshold(String claimType, String region, String metric);
}

interface ClaimRoutingCalculator {
    Map<String, Object> calculate(Map<String, Object> payload);
}

class RoutingDecisionEngine {
    private final ReferenceDataService referenceDataService;
    private final ClaimRoutingCalculator routingCalculator;

    RoutingDecisionEngine(ReferenceDataService referenceDataService, ClaimRoutingCalculator routingCalculator) {
        this.referenceDataService = referenceDataService;
        this.routingCalculator = routingCalculator;
    }

    Map<String, Object> processClaimInitiation(String claimId, Map<String, Object> payload) {
        String claimType = (String) payload.get("claimType");
        String region = (String) payload.get("region");
        double threshold = referenceDataService.getThreshold(claimType, region, "SEVERITY");
        double score = (double) payload.get("severityScore");

        if (score >= threshold) {
            return routingCalculator.calculate(payload);
        }
        return Map.of("id", claimId, "payload", payload, "decision", "LOW", "slaStatus", "STANDARD");
    }
}
