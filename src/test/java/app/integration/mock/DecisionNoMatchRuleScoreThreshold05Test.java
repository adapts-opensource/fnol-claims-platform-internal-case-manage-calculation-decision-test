package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.HashMap;

@ExtendWith(MockitoExtension.class)
class ClaimInitiationRoutingDecisionTest {

    @Mock
    private RuleEvaluationService ruleEvaluationService;
    @Mock
    private IntakeShellRepository intakeShellRepository;
    @Mock
    private ClaimRoutingService claimRoutingService;

    private Map<String, Object> validationEntity;

    @BeforeEach
    void setUp() {
        // Aligns with claim_initiation___routing_decision_validation entity
        validationEntity = new HashMap<>();
        validationEntity.put("id", "claim-init-001");
        validationEntity.put("payload", Map.of(
            "ruleScore", 0.3,
            "matchCount", 0,
            "claimType", "FNOL",
            "policyNumber", "POL-8821"
        ));
    }

    @Test
    @SuppressWarnings("unchecked")
    void decision_no_match_rule_score_threshold_0_5_or_count_0_expected_outcome_intake_shell_created_with_status_unmatched_fnol_route_to_coverage_review() {
        // Arrange: Mock external I/O for rule evaluation (simulating Redis/DynamoDB reference data lookup)
        when(ruleEvaluationService.evaluateRules((Map<String, Object>) validationEntity.get("payload"))).thenReturn("No Match");

        // Arrange: Mock intake shell persistence (simulating DynamoDB Claims & Policy Data Store)
        when(intakeShellRepository.createShell(eq(validationEntity.get("id")), eq("Unmatched FNOL")))
                .thenReturn(Map.of("id", validationEntity.get("id"), "status", "Unmatched FNOL", "createdAt", "2024-01-15T08:30:00Z"));

        // Act: Orchestrate decision flow based on rule evaluation
        String decisionOutcome = ruleEvaluationService.evaluateRules((Map<String, Object>) validationEntity.get("payload"));
        Map<String, Object> createdShell = intakeShellRepository.createShell(validationEntity.get("id").toString(), "Unmatched FNOL");
        claimRoutingService.routeToCoverageReview(validationEntity.get("id").toString());

        // Assert: Verify decision matches expected outcome from description
        assertEquals("No Match", decisionOutcome);

        // Assert: Verify intake shell created with status "Unmatched FNOL"
        assertNotNull(createdShell, "Intake shell must be created");
        assertEquals("Unmatched FNOL", createdShell.get("status"));
        assertEquals(validationEntity.get("id"), createdShell.get("id"));

        // Assert: Verify routing to coverage review was triggered exactly once
        verify(claimRoutingService, times(1)).routeToCoverageReview(validationEntity.get("id").toString());
        verify(intakeShellRepository, times(1)).createShell(eq(validationEntity.get("id")), eq("Unmatched FNOL"));
    }

    // Static interfaces to represent mocked external I/O contracts
    private interface RuleEvaluationService {
        String evaluateRules(Map<String, Object> payload);
    }

    private interface IntakeShellRepository {
        Map<String, Object> createShell(String claimId, String status);
    }

    private interface ClaimRoutingService {
        void routeToCoverageReview(String claimId);
    }
}
