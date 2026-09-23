package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit and integration mock tests for Claim Data Standardization:calculation:transformation.
 * Covers input validation, PAS freshness, exact/fuzzy matching, scoring thresholds,
 * business rules (active/grace period, date-of-loss window), decision routing,
 * and failure output/FNOL status updates.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationCalculationTransformTest {

    @Mock
    private PolicyAdministrationService pasService;
    @Mock
    private RuleEngineDecisionService ruleEngineService;
    @Mock
    private AuditDiaryStore auditStore;
    @Mock
    private WorkflowTaskRouter taskRouter;

    @InjectMocks
    private ClaimDataStandardizationCalculationTransform transformer;

    private Map<String, Object> standardPayload;

    @BeforeEach
    void setUp() {
        standardPayload = Map.of(
            "policyNumber", "POL-88421",
            "riskAddress", "456 Oak Ave, Metropolis, NY",
            "namedInsuredName", "Jane Smith",
            "dateOfLoss", "2024-05-10",
            "productForm", "HO-3",
            "occupancyType", "TENANT"
        );
    }

    @Test
    void inputCriteriaRequired_inputsPolicyNumberInputRiskAddressNamedInsuredNameDateOfLossProductForm() {
        // Given: Valid inputs, PAS returns active policy within 5-min freshness window
        LocalDateTime requestTime = LocalDateTime.now();
        when(pasService.fetchPolicyData(eq("POL-88421"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(Map.of(
                        "status", "ACTIVE",
                        "effectiveDate", "2024-01-01",
                        "expirationDate", "2025-01-01"
                )));

        // When
        Map<String, Object> result = transformer.transform(standardPayload, requestTime);

        // Then: Validation passes, exact match rule triggers, high confidence returned
        assertNotNull(result);
        assertEquals("SINGLE_MATCH_HIGH_CONFIDENCE", result.get("matchStatus"));
        assertNotNull(result.get("matchedPolicyId"));
        assertTrue(((Number) result.get("confidenceScore")).doubleValue() >= 0.90);
        verify(pasService, times(1)).fetchPolicyData(eq("POL-88421"), any());
        verify(auditStore, times(1)).writeAuditEntry(anyString(), anyString());
    }

    @Test
    void multipleMatchesRuleIfCandidateWithScore0_8ReturnMultiple() {
        // Given: No exact policy number match, fuzzy engine returns multiple candidates >= 0.8
        when(pasService.fetchPolicyData(eq("POL-88421"), any()))
                .thenReturn(Optional.empty());
        when(ruleEngineService.queryMatchingPolicies(any(), any()))
                .thenReturn(List.of(
                        Map.of("policyId", "POL-99001", "score", 0.85, "status", "ACTIVE"),
                        Map.of("policyId", "POL-99002", "score", 0.82, "status", "ACTIVE")
                ));

        // When
        Map<String, Object> result = transformer.transform(standardPayload, LocalDateTime.now());

        // Then: Decision routing returns multiple candidates for manual review
        assertEquals("MULTIPLE_MATCHES", result.get("matchStatus"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidatePolicies");
        assertEquals(2, candidates.size());
        verify(ruleEngineService, times(1)).queryMatchingPolicies(any(), any());
        verify(taskRouter, times(1)).routeToManualReview(anyString(), anyList());
    }

    @Test
    void noMatchRuleIfMaxScore0_5ReturnNoMatch() {
        // Given: Exact miss, fuzzy engine returns only low-scoring/inactive policies
        when(pasService.fetchPolicyData(eq("POL-88421"), any()))
                .thenReturn(Optional.empty());
        when(ruleEngineService.queryMatchingPolicies(any(), any()))
                .thenReturn(List.of(Map.of("policyId", "POL-LOW", "score", 0.45, "status", "EXPIRED")));

        // When
        Map<String, Object> result = transformer.transform(standardPayload, LocalDateTime.now());

        // Then: Unmatched status, no routing, FNOL remains open for manual intake
        assertEquals("UNMATCHED", result.get("matchStatus"));
        assertNull(result.get("matchedPolicyId"));
        verify(taskRouter, never()).routeToManualReview(anyString(), anyList());
    }

    @Test
    void failureOutputsMatchingErrorStatusUpdatesFnol() {
        // Given: PAS connectivity failure triggers timeout
        when(pasService.fetchPolicyData(anyString(), any()))
                .thenThrow(new RuntimeException("PAS Gateway Timeout"));

        // When/Then
        ArgumentCaptor<Map<String, Object>> errorCaptor = ArgumentCaptor.forClass(Map.class);
        assertThrows(RuntimeException.class, () -> transformer.transform(standardPayload, LocalDateTime.now()));
        verify(auditStore, times(1)).writeAuditEntry(eq("ERROR"), errorCaptor.capture());
        Map<String, Object> errorPayload = errorCaptor.getValue();
        assertEquals("MATCHING_ERROR", errorPayload.get("matchStatus"));
        verify(taskRouter, times(1)).updateFnolStatus(eq("UNMATCHED"), eq("ERROR"));
    }
}
