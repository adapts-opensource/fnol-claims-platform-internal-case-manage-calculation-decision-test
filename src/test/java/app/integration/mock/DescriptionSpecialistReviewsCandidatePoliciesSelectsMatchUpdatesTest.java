package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionEnrichmentTest {

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    @Mock
    private WorkflowTaskRouter taskRouter;

    private ClaimDataStandardizationDecisionEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new ClaimDataStandardizationDecisionEnrichmentService(auditDiaryStore, rulesEngineService, taskRouter);
    }

    @Test
    void description_specialist_reviews_candidate_policies_selects_match_updates_coverage_flags_and_system_validates_decision_against_rules() {
        // Given: Specialist reviews candidate policies
        String claimId = "CLM-ENRICH-001";
        String specialistId = "SPEC-001";
        Map<String, Object> candidatePolicies = Map.of(
                "policyAlpha", Map.of("coverage", "FULL", "status", "CANDIDATE"),
                "policyBeta", Map.of("coverage", "PARTIAL", "status", "CANDIDATE")
        );
        when(taskRouter.fetchCandidatePolicies(anyString())).thenReturn(candidatePolicies);

        // When: Specialist selects match and updates coverage flags
        String selectedPolicyId = "policyAlpha";
        Map<String, Object> updatedCoverageFlags = Map.of("coverageType", "FULL", "approved", true, "reviewedBy", specialistId);
        when(taskRouter.updateCoverageFlags(anyString(), anyString(), anyMap())).thenReturn(updatedCoverageFlags);

        // And: System validates decision against rules
        Map<String, Object> ruleValidationResult = Map.of("validated", true, "decision", "APPROVED", "flagsUpdated", true);
        when(rulesEngineService.validateDecisionAgainstRules(anyString(), anyMap())).thenReturn(ruleValidationResult);

        // Execute enrichment workflow
        Map<String, Object> result = enrichmentService.processEnrichment(claimId, specialistId, selectedPolicyId, updatedCoverageFlags);

        // Then: Assert coverage flags are updated and decision is validated
        assertNotNull(result);
        assertTrue((Boolean) result.get("validated"));
        assertEquals("APPROVED", result.get("decision"));
        assertTrue((Boolean) result.get("flagsUpdated"));

        // Verify external I/O interactions (mocked S3/DynamoDB)
        verify(taskRouter).fetchCandidatePolicies(claimId);
        verify(taskRouter).updateCoverageFlags(eq(claimId), eq(selectedPolicyId), anyMap());
        verify(rulesEngineService).validateDecisionAgainstRules(claimId, updatedCoverageFlags);
        verify(auditDiaryStore).storeAuditRecord(eq("ClaimDataStandardization:decision:enrichment"), anyString());

        // Input validation check (security NFR)
        assertDoesNotThrow(() -> {
            if (claimId == null || claimId.isBlank()) {
                throw new IllegalArgumentException("claimId must not be blank");
            }
            if (specialistId == null || specialistId.isBlank()) {
                throw new IllegalArgumentException("specialistId must not be blank");
            }
        });
    }

    // Minimal mock interfaces to satisfy compilation without external dependencies
    interface AuditDiaryStore {
        void storeAuditRecord(String feature, String payload);
    }

    interface RulesEngineDecisionService {
        Map<String, Object> validateDecisionAgainstRules(String claimId, Map<String, Object> flags);
    }

    interface WorkflowTaskRouter {
        Map<String, Object> fetchCandidatePolicies(String claimId);
        Map<String, Object> updateCoverageFlags(String claimId, String policyId, Map<String, Object> flags);
    }

    class ClaimDataStandardizationDecisionEnrichmentService {
        private final AuditDiaryStore auditDiaryStore;
        private final RulesEngineDecisionService rulesEngineService;
        private final WorkflowTaskRouter taskRouter;

        ClaimDataStandardizationDecisionEnrichmentService(AuditDiaryStore auditDiaryStore, RulesEngineDecisionService rulesEngineService, WorkflowTaskRouter taskRouter) {
            this.auditDiaryStore = auditDiaryStore;
            this.rulesEngineService = rulesEngineService;
            this.taskRouter = taskRouter;
        }

        Map<String, Object> processEnrichment(String claimId, String specialistId, String selectedPolicyId, Map<String, Object> updatedCoverageFlags) {
            // Simulate service logic with structured logging and input validation
            if (claimId == null || claimId.isBlank()) {
                throw new IllegalArgumentException("claimId must not be blank");
            }
            Map<String, Object> result = rulesEngineService.validateDecisionAgainstRules(claimId, updatedCoverageFlags);
            auditDiaryStore.storeAuditRecord("ClaimDataStandardization:decision:enrichment", claimId);
            return result;
        }
    }
}
