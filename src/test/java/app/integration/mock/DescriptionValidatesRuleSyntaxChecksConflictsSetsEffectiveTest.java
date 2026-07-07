package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MultiChannelFnolSubmissionOrchestrationValidationTest {

    @Mock
    private RuleSyntaxValidator ruleSyntaxValidator;
    @Mock
    private ConflictAnalyzer conflictAnalyzer;
    @Mock
    private EffectiveDateConfigurator effectiveDateConfigurator;
    @Mock
    private RollbackDeploymentManager rollbackDeploymentManager;

    private OrchestrationValidationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new OrchestrationValidationOrchestrator(
                ruleSyntaxValidator,
                conflictAnalyzer,
                effectiveDateConfigurator,
                rollbackDeploymentManager
        );
    }

    @Test
    void description_validates_rule_syntax_checks_conflicts_sets_effective_date_and_deploys_with_rollback_capability() {
        // Given
        String ruleId = "FNOL-RULE-001";
        String rulePayload = "{\"condition\": \"claimAmount > 1000\", \"type\": \"auto\"}";
        LocalDate expectedEffectiveDate = LocalDate.now().plusDays(7);

        // Mock external validation & configuration contracts
        when(ruleSyntaxValidator.validate(rulePayload)).thenReturn(true);
        when(conflictAnalyzer.checkForConflicts(ruleId, rulePayload)).thenReturn(Map.of());
        when(effectiveDateConfigurator.apply(ruleId, expectedEffectiveDate)).thenReturn(true);
        when(rollbackDeploymentManager.deployWithRollback(ruleId, expectedEffectiveDate)).thenReturn(true);

        // When
        boolean deploymentSuccess = orchestrator.executeValidationAndDeployment(ruleId, rulePayload, expectedEffectiveDate);

        // Then
        assertTrue(deploymentSuccess, "Deployment should succeed after all validations pass");
        verify(ruleSyntaxValidator, times(1)).validate(rulePayload);
        verify(conflictAnalyzer, times(1)).checkForConflicts(ruleId, rulePayload);
        verify(effectiveDateConfigurator, times(1)).apply(ruleId, expectedEffectiveDate);
        verify(rollbackDeploymentManager, times(1)).deployWithRollback(ruleId, expectedEffectiveDate);
    }

    // Static dependencies to keep test file self-contained and mockable
    static interface RuleSyntaxValidator {
        boolean validate(String payload);
    }

    static interface ConflictAnalyzer {
        Map<String, String> checkForConflicts(String ruleId, String payload);
    }

    static interface EffectiveDateConfigurator {
        boolean apply(String ruleId, LocalDate date);
    }

    static interface RollbackDeploymentManager {
        boolean deployWithRollback(String ruleId, LocalDate date);
    }

    static class OrchestrationValidationOrchestrator {
        private final RuleSyntaxValidator ruleSyntaxValidator;
        private final ConflictAnalyzer conflictAnalyzer;
        private final EffectiveDateConfigurator effectiveDateConfigurator;
        private final RollbackDeploymentManager rollbackDeploymentManager;

        OrchestrationValidationOrchestrator(RuleSyntaxValidator ruleSyntaxValidator, ConflictAnalyzer conflictAnalyzer,
                                            EffectiveDateConfigurator effectiveDateConfigurator, RollbackDeploymentManager rollbackDeploymentManager) {
            this.ruleSyntaxValidator = ruleSyntaxValidator;
            this.conflictAnalyzer = conflictAnalyzer;
            this.effectiveDateConfigurator = effectiveDateConfigurator;
            this.rollbackDeploymentManager = rollbackDeploymentManager;
        }

        boolean executeValidationAndDeployment(String ruleId, String payload, LocalDate effectiveDate) {
            if (!ruleSyntaxValidator.validate(payload)) {
                throw new IllegalArgumentException("Rule syntax validation failed");
            }
            if (!conflictAnalyzer.checkForConflicts(ruleId, payload).isEmpty()) {
                throw new IllegalStateException("Rule conflict detected");
            }
            if (!effectiveDateConfigurator.apply(ruleId, effectiveDate)) {
                throw new RuntimeException("Failed to set effective date");
            }
            return rollbackDeploymentManager.deployWithRollback(ruleId, effectiveDate);
        }
    }
}
