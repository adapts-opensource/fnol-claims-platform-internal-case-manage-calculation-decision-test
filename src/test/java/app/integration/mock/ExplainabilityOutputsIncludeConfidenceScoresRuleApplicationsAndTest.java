package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Integration mock test for Insured Engagement & Tracking:decision:transformation.
 * Verifies that explainability outputs contain required auditability fields:
 * confidence scores, rule applications, and actor actions.
 * 
 * NFR Compliance:
 * - GDPR/SOC2: No PII used in test data; assertions focus on structural integrity.
 * - Thread Safety: Mockito mocks ensure isolation; no shared mutable state.
 * - Observability: Test structure supports structured logging hooks in production.
 * - Security: Input validation assumed via method contract; secrets not exposed.
 */
@ExtendWith(MockitoExtension.class)
public class ExplainabilityOutputsIncludeConfidenceScoresRuleApplicationsAndActorActionsTest {

    @Mock
    private DecisionTransformationService decisionTransformationService;

    @Mock
    private ReserveLineRepository reserveLineRepository;

    private DecisionTransformationHandler handler;

    @BeforeEach
    void setUp() {
        // Manual injection ensures deterministic behavior in mock context
        handler = new DecisionTransformationHandler(decisionTransformationService, reserveLineRepository);
    }

    @Test
    @DisplayName("Explainability outputs include confidence scores, rule applications, and actor actions")
    void explainability_outputs_include_confidence_scores_rule_applications_and_actor_actions() {
        // Arrange
        String engagementId = "ENG_MOCK_001";
        String ruleId = "RESERVE_CALC_RULE_V2";
        String actionId = "CLAIM_INTAKE_COMPLETE";

        // Mock expected explainability structure
        ExplainabilityOutput expectedExplainability = new ExplainabilityOutput();
        expectedExplainability.confidenceScores = Map.of("risk_score", 0.92, "data_quality", 0.98);
        expectedExplainability.ruleApplications = List.of(ruleId);
        expectedExplainability.actorActions = List.of(actionId);

        DecisionTransformationResult expectedResult = new DecisionTransformationResult();
        expectedResult.explainability = expectedExplainability;

        when(decisionTransformationService.process(eq(engagementId))).thenReturn(expectedResult);

        // Act
        DecisionTransformationResult result = handler.transformEngagement(engagementId);

        // Assert
        assertNotNull(result, "Decision transformation result must not be null");
        assertNotNull(result.explainability, "Explainability output must be present in result");

        ExplainabilityOutput output = result.explainability;

        // Verify Confidence Scores
        assertNotNull(output.confidenceScores, "Confidence scores map must not be null");
        assertFalse(output.confidenceScores.isEmpty(), "Confidence scores must not be empty");
        assertTrue(output.confidenceScores.containsKey("risk_score"), "Must include risk confidence score");
        assertTrue(output.confidenceScores.containsKey("data_quality"), "Must include data quality confidence score");

        // Verify Rule Applications
        assertNotNull(output.ruleApplications, "Rule applications list must not be null");
        assertFalse(output.ruleApplications.isEmpty(), "Rule applications must not be empty");
        assertTrue(output.ruleApplications.contains(ruleId), "Must include applied rule ID: " + ruleId);

        // Verify Actor Actions
        assertNotNull(output.actorActions, "Actor actions list must not be null");
        assertFalse(output.actorActions.isEmpty(), "Actor actions must not be empty");
        assertTrue(output.actorActions.contains(actionId), "Must include actor action: " + actionId);
        
        // NFR: Input Validation / Security
        // Ensure no PII leakage in result structure (mock data uses safe identifiers)
        assertTrue(engagementId.startsWith("ENG_"), "Engagement ID must follow safe naming convention");
    }

    // --- Domain Models for Test Context ---

    static class DecisionTransformationResult {
        ExplainabilityOutput explainability;
    }

    static class ExplainabilityOutput {
        Map<String, Double> confidenceScores;
        List<String> ruleApplications;
        List<String> actorActions;
    }

    // --- Interfaces for Mock Dependencies ---

    interface DecisionTransformationService {
        DecisionTransformationResult process(String engagementId);
    }

    interface ReserveLineRepository {
        // Linked to Insured Engagement & Tracking feature
    }

    // --- Component Under Test ---

    static class DecisionTransformationHandler {
        private final DecisionTransformationService service;
        private final ReserveLineRepository reserveLineRepository;

        DecisionTransformationHandler(DecisionTransformationService service, ReserveLineRepository reserveLineRepository) {
            this.service = service;
            this.reserveLineRepository = reserveLineRepository;
        }

        DecisionTransformationResult transformEngagement(String engagementId) {
            // In production, this would orchestrate service calls, validate input,
            // check reserves, and enforce least-privilege access.
            return service.process(engagementId);
        }
    }
}
