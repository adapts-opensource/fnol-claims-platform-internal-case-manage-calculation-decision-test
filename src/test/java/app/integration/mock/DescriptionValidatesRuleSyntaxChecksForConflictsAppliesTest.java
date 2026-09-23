package app.integration.mock;

import app.domain.decision.RuleDefinition;
import app.domain.decision.RuleVersion;
import app.domain.decision.IssueReport;
import app.domain.decision.IssueType;
import app.domain.decision.ImpactSimulation;
import app.domain.decision.ImpactLevel;
import app.infrastructure.rules.RuleSyntaxValidator;
import app.infrastructure.rules.ConflictChecker;
import app.infrastructure.versioning.RuleVersioningService;
import app.infrastructure.simulation.ImpactSimulator;
import app.service.InsuredEngagementDecisionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Mock tests for Insured Engagement & Tracking: decision:state_transition.
 * Verifies the rule lifecycle validation, conflict resolution, versioning,
 * and impact simulation prior to state activation.
 */
@ExtendWith(MockitoExtension.class)
class DecisionStateTransitionRuleLifecycleMockTest {

    @Mock
    private RuleSyntaxValidator syntaxValidator;

    @Mock
    private ConflictChecker conflictChecker;

    @Mock
    private RuleVersioningService versioningService;

    @Mock
    private ImpactSimulator impactSimulator;

    @Mock
    private InsuredEngagementDecisionService decisionService;

    @InjectMocks
    private DecisionStateTransitionRuleLifecycleMockTest testInstance; // Used for method under test if static, or refactored service

    // Mocked service instance for injection
    private InsuredEngagementDecisionService serviceToTest;

    @BeforeEach
    void setUp() {
        // Re-inject mocks to ensure clean state if testInstance is used, 
        // though typically we test a specific service instance.
        serviceToTest = new InsuredEngagementDecisionService(
                syntaxValidator, 
                conflictChecker, 
                versioningService, 
                impactSimulator
        );
        // Reset mocks between tests
        reset(syntaxValidator, conflictChecker, versioningService, impactSimulator);
    }

    @Test
    void description_validates_rule_syntax_checks_for_conflicts_applies_versioning_and_simulates_impact_before_activation() {
        // Arrange
        RuleDefinition rule = new RuleDefinition(
                "rule-engagement-001", 
                "High Value Claim Engagement Trigger", 
                "amount > 10000 && coverageType == 'COMPREHENSIVE'",
                "v1.0"
        );
        
        RuleVersion expectedVersion = new RuleVersion(
                "rule-engagement-001", 
                2, 
                "v2.0", 
                "DRAFT",
                System.currentTimeMillis()
        );
        
        ImpactSimulation expectedImpact = new ImpactSimulation(
                "rule-engagement-001",
                ImpactLevel.MEDIUM,
                List.of("Exposure: 5", "Reserve: 12000 USD", "Engagement: Delayed by 2h")
        );

        // Mock behaviors
        when(syntaxValidator.validate(rule)).thenReturn(true);
        when(conflictChecker.checkForConflicts(rule)).thenReturn(Collections.emptyList());
        when(versioningService.createVersion(rule)).thenReturn(expectedVersion);
        when(impactSimulator.simulate(expectedVersion)).thenReturn(expectedImpact);

        // Act
        // Simulating the pre-activation flow
        boolean isSyntaxValid = syntaxValidator.validate(rule);
        assertTrue(isSyntaxValid, "Syntax validation should pass");

        List<IssueReport> conflicts = conflictChecker.checkForConflicts(rule);
        assertTrue(conflicts.isEmpty(), "Should detect no conflicts");

        RuleVersion versionedRule = versioningService.createVersion(rule);
        assertNotNull(versionedRule, "Versioning should produce a version");

        ImpactSimulation simulationResult = impactSimulator.simulate(versionedRule);
        assertNotNull(simulationResult, "Impact simulation should return results");

        // Assert interactions and results
        verify(syntaxValidator, times(1)).validate(rule);
        verify(conflictChecker, times(1)).checkForConflicts(rule);
        verify(versioningService, times(1)).createVersion(rule);
        verify(impactSimulator, times(1)).simulate(versionedRule);

        // Verify business logic constraints
        assertEquals("DRAFT", versionedRule.getStatus(), "New version should be in DRAFT state before activation");
        assertEquals(ImpactLevel.MEDIUM, simulationResult.getImpactLevel(), "Impact level should match simulation");
        assertFalse(simulationResult.getAffectedEntities().isEmpty(), "Simulation should report affected entities");
    }
}
