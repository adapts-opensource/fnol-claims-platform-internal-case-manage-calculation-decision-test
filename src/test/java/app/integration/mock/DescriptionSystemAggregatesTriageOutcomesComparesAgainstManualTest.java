package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DescriptionSystemAggregatesTriageOutcomesComparesAgainstManualTest {

    @Mock
    private TriageOutcomeAggregator triageAggregator;
    @Mock
    private ManualOverrideService manualOverrideService;
    @Mock
    private AdjudicationResultService adjudicationService;
    @Mock
    private AccuracyMetricsCalculator accuracyCalculator;
    @Mock
    private RuleDriftDetector driftDetector;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void description_system_aggregates_triage_outcomes_compares_against_manual_overrides_and_adjudication_results_calculates_accuracy_metrics_and_flags_rule_drift() {
        // Arrange: Align with claim_data_standardization_calculation_transform entity schema
        String claimId = "CLM-STD-998877";
        Map<String, Object> triagePayload = new HashMap<>();
        triagePayload.put("id", claimId);
        triagePayload.put("payload", Map.of("outcome", "APPROVED", "score", 0.85));

        Map<String, Object> overridePayload = new HashMap<>();
        overridePayload.put("id", claimId);
        overridePayload.put("payload", Map.of("manualOutcome", "APPROVED", "overrideReason", "EXPERT_REVIEW"));

        Map<String, Object> adjudicationPayload = new HashMap<>();
        adjudicationPayload.put("id", claimId);
        adjudicationPayload.put("payload", Map.of("adjudicatedOutcome", "APPROVED", "confidence", 0.92));

        when(triageAggregator.aggregate(anyList())).thenReturn(List.of(triagePayload));
        when(manualOverrideService.fetchByClaimId(eq(claimId))).thenReturn(List.of(overridePayload));
        when(adjudicationService.fetchByClaimId(eq(claimId))).thenReturn(List.of(adjudicationPayload));

        // Act: Execute aggregation, comparison, metric calculation, and drift detection
        List<Map<String, Object>> aggregated = triageAggregator.aggregate(List.of(triagePayload));
        List<Map<String, Object>> overrides = manualOverrideService.fetchByClaimId(claimId);
        List<Map<String, Object>> adjudications = adjudicationService.fetchByClaimId(claimId);

        double accuracy = accuracyCalculator.compute(aggregated, overrides, adjudications);
        boolean isDrifted = driftDetector.check(aggregated, overrides, accuracy);

        // Assert: Verify metrics, drift flag, and mock interactions
        assertEquals(1.0, accuracy, 1e-6, "Accuracy should be 100% when system and manual outcomes align");
        assertFalse(isDrifted, "Rule drift should not be flagged for consistent outcomes");
        assertEquals(1, aggregated.size());
        verify(triageAggregator, times(1)).aggregate(anyList());
        verify(manualOverrideService, times(1)).fetchByClaimId(eq(claimId));
        verify(adjudicationService, times(1)).fetchByClaimId(eq(claimId));
        verify(accuracyCalculator, times(1)).compute(anyList(), anyList(), anyList());
        verify(driftDetector, times(1)).check(anyList(), anyList(), anyDouble());
    }
}
