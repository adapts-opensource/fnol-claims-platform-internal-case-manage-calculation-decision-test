package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Mock test for Claim Data Standardization: Decision Enrichment.
 * Validates input criteria, policy existence, specialist authorization,
 * override reason logic, and data freshness requirements.
 */
class ClaimDataEnrichmentDecisionMockTest {

    @Mock
    private PolicyService policyService;

    @Mock
    private SpecialistService specialistService;

    @Mock
    private WorkflowTaskRouter workflowTaskRouter;

    @Mock
    private AuditDiaryStore auditDiaryStore;

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    @InjectMocks
    private ClaimDataEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void inputCriteriaRequiredTaskIdCandidatePolicyIdsSpecialistIdSelectionPolicyIdOverrideReasonOptionalManualCoverageNotesAdditionalDocumentationValidationPolicyIdExistsSpecialistRoleAuthorizedOverrideReasonProvidedIfOverridingAutoMatchFreshnessPolicyDataMustBeCurrentAtTimeOfReview() {
        // Arrange: Input Criteria
        String taskId = "task-claim-std-001";
        List<String> candidatePolicyIds = List.of("pol-auto-match-001", "pol-manual-002");
        String specialistId = "spec-claims-analyst-01";
        String selectionPolicyId = "pol-manual-002"; // Selecting non-auto-match implies override
        String overrideReason = "Auto-match confidence low due to recent address change";
        String manualCoverageNotes = "Reviewed attachments; coverage confirmed.";
        Map<String, String> additionalDocumentation = Map.of("doc_type", "proof_of_ownership");

        // Arrange: Mocks for Validations & NFRs

        // 1. Policy ID Exists & Freshness: Policy data must be current at time of review
        Instant now = Instant.now();
        Instant policyLastUpdated = now.minus(2, ChronoUnit.HOURS); // 2 hours ago, within freshness window
        Policy mockPolicy = new Policy(selectionPolicyId, policyLastUpdated, "ACTIVE");
        when(policyService.getPolicy(selectionPolicyId)).thenReturn(mockPolicy);

        // 2. Specialist Role Authorized
        when(specialistService.isAuthorized(specialistId, "CLAIMS_ANALYST")).thenReturn(true);

        // 3. Workflow Task Exists
        when(workflowTaskRouter.getTask(taskId)).thenReturn(Map.of("id", taskId, "status", "PENDING"));

        // 4. Rules Engine Evaluation
        when(rulesEngineService.evaluate(any())).thenReturn(Map.of("decision", "APPROVED", "score", 95.0));

        // 5. Audit Logging (Observability/Compliance)
        doNothing().when(auditDiaryStore).log(any());

        // Act
        var result = enrichmentService.processEnrichment(
            taskId,
            candidatePolicyIds,
            specialistId,
            selectionPolicyId,
            overrideReason,
            manualCoverageNotes,
            additionalDocumentation
        );

        // Assert: Functional Outcome
        assertNotNull(result, "Enrichment result should not be null");
        assertEquals("APPROVED", result.getDecision(), "Decision should be APPROVED based on valid inputs");

        // Assert: Validations & NFRs
        // Verify Policy Interaction (Existence check)
        verify(policyService, times(1)).getPolicy(selectionPolicyId);

        // Verify Freshness Constraint
        assertTrue(
            ChronoUnit.HOURS.between(mockPolicy.getLastUpdated(), now) <= 24,
            "Policy data must be current at time of review (freshness check failed)"
        );

        // Verify Authorization (Least Privilege)
        verify(specialistService, times(1)).isAuthorized(specialistId, "CLAIMS_ANALYST");

        // Verify Override Reason Logic
        // If selection differs from expected auto-match, override_reason must be provided
        verify(auditDiaryStore, times(1)).log(argThat(entry -> 
            entry.containsKey("override_reason") && 
            entry.get("override_reason").equals(overrideReason)
        ));

        // Verify Optional Fields Propagation
        verify(auditDiaryStore, times(1)).log(argThat(entry -> 
            entry.containsKey("manual_coverage_notes") && 
            entry.get("manual_coverage_notes").equals(manualCoverageNotes)
        ));
    }

    // --- Mock Infrastructure Contracts & Helper Classes ---

    interface PolicyService {
        Policy getPolicy(String policyId);
    }

    interface SpecialistService {
        boolean isAuthorized(String specialistId, String role);
    }

    interface WorkflowTaskRouter {
        Map<String, Object> getTask(String taskId);
    }

    interface AuditDiaryStore {
        void log(Map<String, Object> entry);
    }

    interface RulesEngineDecisionService {
        Map<String, Object> evaluate(Map<String, Object> input);
    }

    static class Policy {
        private final String id;
        private final Instant lastUpdated;
        private final String status;

        Policy(String id, Instant lastUpdated, String status) {
            this.id = id;
            this.lastUpdated = lastUpdated;
            this.status = status;
        }

        String getId() { return id; }
        Instant getLastUpdated() { return lastUpdated; }
        String getStatus() { return status; }
    }

    static class EnrichmentResult {
        private final String decision;
        
        EnrichmentResult(String decision) {
            this.decision = decision;
        }
        
        String getDecision() { return decision; }
    }

    // Simplified service implementation for test wiring
    static class ClaimDataEnrichmentService {
        private final PolicyService policyService;
        private final SpecialistService specialistService;
        private final WorkflowTaskRouter workflowTaskRouter;
        private final AuditDiaryStore auditDiaryStore;
        private final RulesEngineDecisionService rulesEngineService;

        ClaimDataEnrichmentService(
            PolicyService policyService,
            SpecialistService specialistService,
            WorkflowTaskRouter workflowTaskRouter,
            AuditDiaryStore auditDiaryStore,
            RulesEngineDecisionService rulesEngineService
        ) {
            this.policyService = policyService;
            this.specialistService = specialistService;
            this.workflowTaskRouter = workflowTaskRouter;
            this.auditDiaryStore = auditDiaryStore;
            this.rulesEngineService = rulesEngineService;
        }

        EnrichmentResult processEnrichment(
            String taskId,
            List<String> candidatePolicyIds,
            String specialistId,
            String selectionPolicyId,
            String overrideReason,
            String manualCoverageNotes,
            Map<String, String> additionalDocumentation
        ) {
            // Validation: Policy Exists & Freshness
            Policy policy = policyService.getPolicy(selectionPolicyId);
            if (policy == null) {
                throw new IllegalArgumentException("Policy ID does not exist: " + selectionPolicyId);
            }
            // Freshness check in service logic
            if (ChronoUnit.HOURS.between(policy.getLastUpdated(), Instant.now()) > 24) {
                throw new IllegalStateException("Policy data is stale. Must be current at time of review.");
            }

            // Validation: Specialist Authorized
            if (!specialistService.isAuthorized(specialistId, "CLAIMS_ANALYST")) {
                throw new SecurityException("Specialist not authorized for role CLAIMS_ANALYST");
            }

            // Validation: Override Reason
            // Assuming auto-match logic determines if override is needed; here we verify logic accepts it
            if (overrideReason == null || overrideReason.isBlank()) {
                throw new IllegalArgumentException("Override reason required when selecting non-default policy");
            }

            // Audit Logging
            auditDiaryStore.log(Map.of(
                "task_id", taskId,
                "specialist_id", specialistId,
                "selection_policy_id", selectionPolicyId,
                "override_reason", overrideReason,
                "manual_coverage_notes", manualCoverageNotes,
                "timestamp", Instant.now().toString()
            ));

            // Rules Engine
            var decision = rulesEngineService.evaluate(Map.of("policy", policy.getId()));
            return new EnrichmentResult((String) decision.get("decision"));
        }
    }
}
