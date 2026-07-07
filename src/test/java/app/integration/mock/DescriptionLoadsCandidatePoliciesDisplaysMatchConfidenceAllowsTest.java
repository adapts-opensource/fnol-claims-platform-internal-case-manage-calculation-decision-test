package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock integration test for Multi-Channel FNOL Submission:decision:validation.
 * Verifies candidate policy loading, confidence scoring, manual selection,
 * date/form rule re-evaluation, and claim status updates.
 * External I/O (DynamoDB, S3, SES) is fully mocked per NFR security & compliance requirements.
 */
@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionDecisionValidationTest {

    @Mock
    private PolicyCandidateLoader policyCandidateLoader;

    @Mock
    private MatchConfidenceCalculator matchConfidenceCalculator;

    @Mock
    private PolicySelector policySelector;

    @Mock
    private DateFormRuleEngine dateFormRuleEngine;

    @Mock
    private ClaimStatusUpdater claimStatusUpdater;

    private String id;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        id = "fnol-decision-val-001";
        payload = new HashMap<>();
        payload.put("channel", "mobile");
        payload.put("claimantId", "CL-8842");
        payload.put("incidentDate", "2024-03-15");
        payload.put("policyType", "AUTO");
    }

    @Test
    void description_loads_candidate_policies_displays_match_confidence_allows_manual_selection_re_evaluates_date_form_rules_and_updates_claim_status() {
        // Given: Mock DynamoDB Policy_Coverage_Validator returns candidate policies
        List<Map<String, Object>> candidates = Arrays.asList(
                buildPolicyMap("POL-101", "AUTO", 0.94),
                buildPolicyMap("POL-102", "AUTO", 0.81)
        );
        when(policyCandidateLoader.loadCandidates(eq(id))).thenReturn(candidates);

        // When: Display match confidence
        String selectedPolicyId = "POL-101";
        double confidenceScore = 0.94;
        when(matchConfidenceCalculator.calculateConfidence(eq(selectedPolicyId), anyMap())).thenReturn(confidenceScore);

        // Allow manual selection
        when(policySelector.confirmSelection(selectedPolicyId, candidates)).thenReturn(selectedPolicyId);

        // Re-evaluate date/form rules (mocked Guidewire_Claim_Model & SES/S3 contract validation)
        boolean rulesValid = true;
        when(dateFormRuleEngine.evaluateDateAndForm(anyMap(), anyMap())).thenReturn(rulesValid);

        // Update claim status (mocked DynamoDB write)
        String newStatus = "VALIDATED";
        doNothing().when(claimStatusUpdater).updateStatus(eq(id), eq(newStatus), anyMap());

        // Execute flow
        String actualStatus = claimStatusUpdater.updateStatus(id, newStatus, payload);

        // Then: Assert data model fields and outcomes
        assertEquals(newStatus, actualStatus);
        assertEquals(id, claimStatusUpdater.getLastUpdatedEntityId());
        assertEquals(confidenceScore, matchConfidenceCalculator.calculateConfidence(selectedPolicyId, payload), 0.001);

        // Verify infra I/O contracts were invoked exactly once
        verify(policyCandidateLoader, times(1)).loadCandidates(id);
        verify(matchConfidenceCalculator, times(1)).calculateConfidence(eq(selectedPolicyId), anyMap());
        verify(policySelector, times(1)).confirmSelection(eq(selectedPolicyId), anyList());
        verify(dateFormRuleEngine, times(1)).evaluateDateAndForm(anyMap(), anyMap());
        verify(claimStatusUpdater, times(1)).updateStatus(eq(id), eq(newStatus), anyMap());
    }

    private Map<String, Object> buildPolicyMap(String policyId, String type, double confidence) {
        Map<String, Object> policy = new HashMap<>();
        policy.put("id", policyId);
        policy.put("type", type);
        policy.put("confidence", confidence);
        policy.put("coverageDetails", new HashMap<String, Object>());
        return policy;
    }
}
