package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClaimDataStandardizationEnrichmentDecisionTest {

    @Mock
    private ClaimEnrichmentDecisionService enrichmentDecisionService;

    private Map<String, Object> successPayload;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        successPayload = Map.of(
            "policy_id", "POL-8842-INC",
            "match_confidence_score", 0.92,
            "coverage_flags", Set.of("FULL_COVERAGE", "ACTIVE"),
            "date_validation_result", "WITHIN_WINDOW",
            "triage_path", "EXPEDITED_REVIEW"
        );
        when(enrichmentDecisionService.processEnrichment(any(Map.class))).thenReturn(successPayload);
    }

    @Test
    void output_criteria_success_outputs_policy_id_match_confidence_score_coverage_flags_date_validation_result_triage_path_failure_outputs_unmatched_shell_id_resolve_task_id_coverage_review_flag_error_code() {
        // Arrange
        Map<String, Object> inputClaimData = Map.of("claimId", "CLM-TEST-001", "status", "SUBMITTED");

        // Act
        Map<String, Object> actualResult = enrichmentDecisionService.processEnrichment(inputClaimData);

        // Assert Success Outputs
        assertNotNull(actualResult.get("policy_id"), "policy_id must be present in success outputs");
        assertEquals("POL-8842-INC", actualResult.get("policy_id"));

        assertNotNull(actualResult.get("match_confidence_score"), "match_confidence_score must be present in success outputs");
        assertEquals(0.92, actualResult.get("match_confidence_score"));

        assertNotNull(actualResult.get("coverage_flags"), "coverage_flags must be present in success outputs");
        assertTrue(((Set<?>) actualResult.get("coverage_flags")).contains("FULL_COVERAGE"));

        assertNotNull(actualResult.get("date_validation_result"), "date_validation_result must be present in success outputs");
        assertEquals("WITHIN_WINDOW", actualResult.get("date_validation_result"));

        assertNotNull(actualResult.get("triage_path"), "triage_path must be present in success outputs");
        assertEquals("EXPEDITED_REVIEW", actualResult.get("triage_path"));

        // Assert Failure Outputs are absent/null
        assertNull(actualResult.get("unmatched_shell_id"), "unmatched_shell_id should be null on success");
        assertNull(actualResult.get("resolve_task_id"), "resolve_task_id should be null on success");
        assertNull(actualResult.get("coverage_review_flag"), "coverage_review_flag should be null on success");
        assertNull(actualResult.get("error_code"), "error_code should be null on success");
    }
}
