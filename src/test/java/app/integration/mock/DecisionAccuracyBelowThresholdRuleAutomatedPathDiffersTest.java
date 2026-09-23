package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class DecisionAccuracyBelowThresholdRuleAutomatedPathDiffersTest {

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    private ClaimDecisionEnrichmentProcessor enrichmentProcessor;

    @BeforeEach
    void setUp() {
        enrichmentProcessor = new ClaimDecisionEnrichmentProcessor(rulesEngineService, workflowTaskRouter, auditDiaryStore);
    }

    @Test
    void decision_accuracy_below_threshold_rule_automated_path_differs_from_final_path_15_expected_outcome_flag_for_rule_tuning() {
        // Arrange
        String claimId = "CLM-ENRICH-001";
        double automatedPathAccuracy = 0.68;
        double finalPathAccuracy = 0.92;
        double discrepancy = Math.abs(finalPathAccuracy - automatedPathAccuracy); // 0.24 (24%)

        // Mock external I/O contracts (DynamoDB & S3)
        when(rulesEngineService.getItemPayload(claimId)).thenReturn(Map.of("accuracy", finalPathAccuracy));
        when(workflowTaskRouter.getRoutingConfig(claimId)).thenReturn(Map.of("auto_path", automatedPathAccuracy));
        when(auditDiaryStore.writeObject(anyString(), anyString())).thenReturn("s3://AuditDiaryStore-bucket/AuditDiaryStore/CLM-ENRICH-001.json");

        // Act
        DecisionOutcome outcome = enrichmentProcessor.evaluateAndEnrich(claimId);

        // Assert
        assertTrue(outcome.isFlagForRuleTuning(), "Expected flag for rule tuning when discrepancy > 15%");
        assertEquals(discrepancy, outcome.getDiscrepancyPercentage(), 0.0001, "Discrepancy should accurately reflect path difference");
        assertEquals("FLAG_FOR_RULE_TUNING", outcome.getDecisionStatus(), "Decision status must match expected outcome");

        // Verify infra interactions
        verify(rulesEngineService).getItemPayload(claimId);
        verify(workflowTaskRouter).getRoutingConfig(claimId);
        verify(auditDiaryStore).writeObject(eq(claimId), anyString());
    }
}

// Internal stubs to satisfy compilation and represent infra contracts
interface RulesEngineDecisionService { Map<String, Object> getItemPayload(String key); }
interface WorkflowTaskRouter { Map<String, Object> getRoutingConfig(String key); }
interface AuditDiaryStore { String writeObject(String bucketName, String objectKeyPattern); }

class ClaimDecisionEnrichmentProcessor {
    private final RulesEngineDecisionService rulesEngineService;
    private final WorkflowTaskRouter workflowTaskRouter;
    private final AuditDiaryStore auditDiaryStore;

    ClaimDecisionEnrichmentProcessor(RulesEngineDecisionService rulesEngineService,
                                     WorkflowTaskRouter workflowTaskRouter,
                                     AuditDiaryStore auditDiaryStore) {
        this.rulesEngineService = rulesEngineService;
        this.workflowTaskRouter = workflowTaskRouter;
        this.auditDiaryStore = auditDiaryStore;
    }

    DecisionOutcome evaluateAndEnrich(String claimId) {
        Map<String, Object> finalData = rulesEngineService.getItemPayload(claimId);
        Map<String, Object> autoData = workflowTaskRouter.getRoutingConfig(claimId);

        double finalAccuracy = (double) finalData.get("accuracy");
        double autoAccuracy = (double) autoData.get("auto_path");
        double discrepancy = Math.abs(finalAccuracy - autoAccuracy);

        boolean shouldFlag = discrepancy > 0.15;
        String status = shouldFlag ? "FLAG_FOR_RULE_TUNING" : "APPROVED";

        auditDiaryStore.writeObject("AuditDiaryStore-bucket", claimId + ".json");

        DecisionOutcome outcome = new DecisionOutcome();
        outcome.setFlagForRuleTuning(shouldFlag);
        outcome.setDiscrepancyPercentage(discrepancy);
        outcome.setDecisionStatus(status);
        return outcome;
    }
}

class DecisionOutcome {
    private boolean flagForRuleTuning;
    private double discrepancyPercentage;
    private String decisionStatus;

    public boolean isFlagForRuleTuning() { return flagForRuleTuning; }
    public void setFlagForRuleTuning(boolean flagForRuleTuning) { this.flagForRuleTuning = flagForRuleTuning; }
    public double getDiscrepancyPercentage() { return discrepancyPercentage; }
    public void setDiscrepancyPercentage(double discrepancyPercentage) { this.discrepancyPercentage = discrepancyPercentage; }
    public String getDecisionStatus() { return decisionStatus; }
    public void setDecisionStatus(String decisionStatus) { this.decisionStatus = decisionStatus; }
}
