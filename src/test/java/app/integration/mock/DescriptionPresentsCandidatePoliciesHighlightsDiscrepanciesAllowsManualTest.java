package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Domain types for Multi-Channel FNOL Submission
record CandidatePolicy(String id, String coverageType, double limit) {}
record Discrepancy(String field, String submittedValue, String policyValue) {}
record ClaimContext(String claimId, String selectedPolicyId, String currentState, Map<String, Object> metadata) {}

// Mocked external I/O contracts (S3, DynamoDB, SES) - never called in test
interface S3MockContract {}
interface DynamoDbMockContract {}
interface SesMockContract {}

// Business interfaces for orchestration/validation
interface PolicyCandidateProvider {
    List<CandidatePolicy> fetchCandidates(String claimId);
}

interface DiscrepancyAnalyzer {
    List<Discrepancy> analyzeDiscrepancies(String claimId, CandidatePolicy policy);
}

interface ClaimContextUpdater {
    void updateContext(ClaimContext context);
}

interface StateTransitionStore {
    void persistStateTransition(String id, Map<String, Object> payload);
}

class FnolOrchestrationValidationService {
    private final PolicyCandidateProvider policyProvider;
    private final DiscrepancyAnalyzer discrepancyAnalyzer;
    private final ClaimContextUpdater contextUpdater;
    private final StateTransitionStore transitionStore;

    FnolOrchestrationValidationService(PolicyCandidateProvider policyProvider, DiscrepancyAnalyzer discrepancyAnalyzer, ClaimContextUpdater contextUpdater, StateTransitionStore transitionStore) {
        this.policyProvider = policyProvider;
        this.discrepancyAnalyzer = discrepancyAnalyzer;
        this.contextUpdater = contextUpdater;
        this.transitionStore = transitionStore;
    }

    void validateAndSelectPolicy(String claimId, String manualPolicyId) {
        // 1. Presents candidate policies
        List<CandidatePolicy> candidates = policyProvider.fetchCandidates(claimId);
        assertNotNull(candidates, "Candidate policies must be presented");
        assertFalse(candidates.isEmpty(), "At least one candidate policy should be available");

        // 2. Highlights discrepancies for the manually selected policy
        CandidatePolicy selectedPolicy = candidates.stream()
                .filter(p -> p.id().equals(manualPolicyId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected policy not found in candidates"));

        List<Discrepancy> discrepancies = discrepancyAnalyzer.analyzeDiscrepancies(claimId, selectedPolicy);
        assertNotNull(discrepancies, "Discrepancies must be highlighted for the selected policy");

        // 3. Updates claim context with manual selection and discrepancy metadata
        ClaimContext context = new ClaimContext(claimId, manualPolicyId, "VALIDATION_COMPLETE", Map.of("discrepancies", discrepancies));
        contextUpdater.updateContext(context);

        // 4. Persists state transition to data store
        Map<String, Object> payload = Map.of(
                "id", "trans_" + claimId,
                "payload", Map.of("claimId", claimId, "selectedPolicyId", manualPolicyId, "state", "VALIDATION_COMPLETE")
        );
        transitionStore.persistStateTransition(payload.get("id").toString(), payload);
    }
}

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolOrchestrationValidationTest {

    @Mock private PolicyCandidateProvider policyProvider;
    @Mock private DiscrepancyAnalyzer discrepancyAnalyzer;
    @Mock private ClaimContextUpdater contextUpdater;
    @Mock private StateTransitionStore transitionStore;
    @Mock private S3MockContract s3Client;
    @Mock private DynamoDbMockContract dynamoDbClient;
    @Mock private SesMockContract sesClient;

    private FnolOrchestrationValidationService service;

    @BeforeEach
    void setUp() {
        service = new FnolOrchestrationValidationService(policyProvider, discrepancyAnalyzer, contextUpdater, transitionStore);
    }

    @Test
    void description_presents_candidate_policies_highlights_discrepancies_allows_manual_selection_and_updates_claim_context() {
        // Arrange
        String claimId = "CLM-2024-001";
        String policyIdA = "POL-A";
        String policyIdB = "POL-B";
        String selectedPolicyId = policyIdB;

        CandidatePolicy candidateA = new CandidatePolicy(policyIdA, "AUTO", 50000.0);
        CandidatePolicy candidateB = new CandidatePolicy(policyIdB, "AUTO", 75000.0);
        List<CandidatePolicy> candidates = List.of(candidateA, candidateB);

        Discrepancy discrepancy = new Discrepancy("deductible", "500", "1000");
        List<Discrepancy> discrepancies = List.of(discrepancy);

        when(policyProvider.fetchCandidates(claimId)).thenReturn(candidates);
        when(discrepancyAnalyzer.analyzeDiscrepancies(eq(claimId), argThat(p -> p.id().equals(selectedPolicyId)))).thenReturn(discrepancies);

        // Act
        service.validateAndSelectPolicy(claimId, selectedPolicyId);

        // Assert - Presents candidate policies
        verify(policyProvider).fetchCandidates(claimId);

        // Assert - Highlights discrepancies
        verify(discrepancyAnalyzer).analyzeDiscrepancies(eq(claimId), argThat(p -> p.id().equals(selectedPolicyId)));

        // Assert - Updates claim context
        ArgumentCaptor<ClaimContext> contextCaptor = ArgumentCaptor.forClass(ClaimContext.class);
        verify(contextUpdater).updateContext(contextCaptor.capture());
        ClaimContext capturedContext = contextCaptor.getValue();
        assertEquals(claimId, capturedContext.claimId());
        assertEquals(selectedPolicyId, capturedContext.selectedPolicyId());
        assertEquals("VALIDATION_COMPLETE", capturedContext.currentState());
        assertNotNull(capturedContext.metadata().get("discrepancies"));

        // Assert - Persists state transition
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(transitionStore).persistStateTransition(anyString(), payloadCaptor.capture());
        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertEquals("trans_" + claimId, capturedPayload.get("id"));
        Map<String, Object> nestedPayload = (Map<String, Object>) capturedPayload.get("payload");
        assertEquals(claimId, nestedPayload.get("claimId"));
        assertEquals(selectedPolicyId, nestedPayload.get("selectedPolicyId"));

        // Verify external I/O contracts are mocked and never called
        verifyNoInteractions(s3Client, dynamoDbClient, sesClient);
    }
}
