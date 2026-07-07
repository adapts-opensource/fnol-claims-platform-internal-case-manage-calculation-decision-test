package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimDataStandardizationStateTransitionOrchestrationInputValidationTest {

    @Mock
    private ClaimDataStoreClient claimDataStoreClient;

    @Mock
    private DocumentManagementClient documentManagementClient;

    private ClaimOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimOrchestrationService(claimDataStoreClient, documentManagementClient);
    }

    @Test
    void input_criteria_required_inputs_candidate_claim_ids_reviewer_decision_optional_inputs_merge_reason_override_notes_input_validation_candidate_claim_ids_must_be_active_or_recently_closed_reviewer_decision_must_be_merge_or_continue_freshness_requirements_candidate_claims_must_be_fetched_in_real_time_prior_decisions_must_be_visible_within_last_12_months() {
        // 1. Valid inputs with optional fields present
        Map<String, String> validPayload = Map.of(
            "candidate_claim_ids", "claim_A,claim_B",
            "reviewer_decision", "merge",
            "merge_reason", "System duplicate detected",
            "override_notes": "Approved by senior reviewer"
        );
        when(claimDataStoreClient.fetchClaimData("claim_A")).thenReturn(Map.of("status", "ACTIVE"));
        when(claimDataStoreClient.fetchClaimData("claim_B")).thenReturn(Map.of("status", "CLOSED", "closed_at", LocalDateTime.now().minusDays(5).toString()));
        when(claimDataStoreClient.fetchPriorDecisions("claim_A")).thenReturn(List.of(Map.of("decision_date", LocalDateTime.now().minusMonths(3).toString())));
        assertDoesNotThrow(() -> orchestrationService.validateInputs(validPayload));

        // 2. Missing required inputs
        Map<String, String> missingRequired = Map.of("merge_reason", "test");
        assertThrows(IllegalArgumentException.class, () -> orchestrationService.validateInputs(missingRequired));

        // 3. Invalid candidate_claim_ids status (must be active or recently closed)
        Map<String, String> invalidStatusPayload = Map.of(
            "candidate_claim_ids", "claim_C",
            "reviewer_decision", "merge"
        );
        when(claimDataStoreClient.fetchClaimData("claim_C")).thenReturn(Map.of("status", "CANCELLED"));
        assertThrows(IllegalArgumentException.class, () -> orchestrationService.validateInputs(invalidStatusPayload));

        // 4. Invalid reviewer_decision (must be merge or continue)
        Map<String, String> invalidDecisionPayload = Map.of(
            "candidate_claim_ids", "claim_A",
            "reviewer_decision", "reject"
        );
        assertThrows(IllegalArgumentException.class, () -> orchestrationService.validateInputs(invalidDecisionPayload));

        // 5. Prior decisions outside 12 months freshness window
        Map<String, String> staleDecisionsPayload = Map.of(
            "candidate_claim_ids", "claim_A",
            "reviewer_decision", "continue"
        );
        when(claimDataStoreClient.fetchPriorDecisions("claim_A")).thenReturn(List.of(Map.of("decision_date", LocalDateTime.now().minusYears(13).toString())));
        assertThrows(IllegalArgumentException.class, () -> orchestrationService.validateInputs(staleDecisionsPayload));

        // 6. Verify real-time fetch simulation occurred before validation
        verify(claimDataStoreClient, times(1)).fetchClaimData("claim_A");
        verify(claimDataStoreClient, times(1)).fetchPriorDecisions("claim_A");
    }
}

// Minimal interfaces to represent mocked external I/O contracts
interface ClaimDataStoreClient {
    Map<String, String> fetchClaimData(String claimId);
    List<Map<String, String>> fetchPriorDecisions(String claimId);
}

interface DocumentManagementClient {
    // Placeholder for S3 Document Management integration
}

// Service orchestration layer containing validation logic
class ClaimOrchestrationService {
    private final ClaimDataStoreClient claimDataStoreClient;
    private final DocumentManagementClient documentManagementClient;

    public ClaimOrchestrationService(ClaimDataStoreClient claimDataStoreClient, DocumentManagementClient documentManagementClient) {
        this.claimDataStoreClient = claimDataStoreClient;
        this.documentManagementClient = documentManagementClient;
    }

    public void validateInputs(Map<String, String> payload) {
        Set<String> requiredInputs = Set.of("candidate_claim_ids", "reviewer_decision");
        for (String key : requiredInputs) {
            if (!payload.containsKey(key) || payload.get(key) == null || payload.get(key).isBlank()) {
                throw new IllegalArgumentException("Required input missing: " + key);
            }
        }

        String candidateClaimIds = payload.get("candidate_claim_ids");
        String reviewerDecision = payload.get("reviewer_decision");

        // Real-time fetch simulation & status validation
        for (String claimId : candidateClaimIds.split(",")) {
            Map<String, String> claimData = claimDataStoreClient.fetchClaimData(claimId.trim());
            String status = claimData.get("status");
            if (!"ACTIVE".equals(status) && !"CLOSED".equals(status)) {
                throw new IllegalArgumentException("candidate_claim_ids must be active or recently closed");
            }
            if ("CLOSED".equals(status)) {
                String closedAt = claimData.get("closed_at");
                if (closedAt != null && LocalDateTime.parse(closedAt).isBefore(LocalDateTime.now().minusDays(30))) {
                    throw new IllegalArgumentException("candidate_claim_ids must be active or recently closed");
                }
            }
        }

        // Reviewer decision validation
        if (!"merge".equalsIgnoreCase(reviewerDecision) && !"continue".equalsIgnoreCase(reviewerDecision)) {
            throw new IllegalArgumentException("reviewer_decision must be merge or continue");
        }

        // Freshness: Prior decisions visibility within last 12 months
        for (String claimId : candidateClaimIds.split(",")) {
            List<Map<String, String>> decisions = claimDataStoreClient.fetchPriorDecisions(claimId.trim());
            for (Map<String, String> decision : decisions) {
                String decisionDate = decision.get("decision_date");
                if (decisionDate != null && LocalDateTime.parse(decisionDate).isBefore(LocalDateTime.now().minusMonths(12))) {
                    throw new IllegalArgumentException("Prior decisions must be visible within last 12 months");
                }
            }
        }
    }
}
