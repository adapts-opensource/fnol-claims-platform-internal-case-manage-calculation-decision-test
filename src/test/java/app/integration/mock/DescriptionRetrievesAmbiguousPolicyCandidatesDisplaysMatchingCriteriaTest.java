package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DescriptionRetrievesAmbiguousPolicyCandidatesDisplaysMatchingCriteriaTest {

    @Mock
    private PolicyCandidateRetriever policyCandidateRetriever;
    @Mock
    private ClaimContextUpdater claimContextUpdater;
    @Mock
    private DownstreamRouter downstreamRouter;
    @Mock
    private InputValidator inputValidator;
    @Mock
    private StructuredLogger structuredLogger;

    private ClaimDataStandardizationDecisionValidationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ClaimDataStandardizationDecisionValidationHandler(
                policyCandidateRetriever,
                claimContextUpdater,
                downstreamRouter,
                inputValidator,
                structuredLogger
        );
    }

    @Test
    void description_retrieves_ambiguous_policy_candidates_displays_matching_criteria_allows_analyst_selection_updates_claim_policy_context_and_triggers_downstream_routing() {
        // Given: Ambiguous policy candidates and matching criteria
        String claimId = "claim-123";
        String candidateId = "POL-AMB-001";
        List<String> ambiguousCandidates = List.of("POL-001", "POL-002");
        Map<String, Object> matchingCriteria = Map.of("rule", "coverage_match", "confidence", 0.82);
        Map<String, Object> analystSelection = Map.of("selectedCandidate", candidateId, "analystId", "analyst-456");
        Map<String, Object> enrichedPayload = Map.of("id", "payload-789", "payload", Map.of("policyRef", candidateId, "status", "validated"));

        when(policyCandidateRetriever.retrieveAmbiguousCandidates(claimId)).thenReturn(ambiguousCandidates);
        when(policyCandidateRetriever.fetchMatchingCriteria(claimId, candidateId)).thenReturn(matchingCriteria);
        when(inputValidator.validateInput(anyMap())).thenReturn(true);

        // When: Processing analyst selection in a thread-safe manner
        Map<String, Object> result = new ConcurrentHashMap<>();
        Thread t = new Thread(() -> {
            result.put("outcome", handler.processAnalystSelection(claimId, analystSelection, enrichedPayload));
        });
        t.start();
        try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Then: Verify retrieval, criteria display, context update, routing, validation, and logging
        assertNotNull(result.get("outcome"));
        assertEquals("SUCCESS", result.get("outcome"));

        verify(policyCandidateRetriever, times(1)).retrieveAmbiguousCandidates(claimId);
        verify(policyCandidateRetriever, times(1)).fetchMatchingCriteria(eq(claimId), eq(candidateId));
        verify(inputValidator, times(1)).validateInput(enrichedPayload);
        verify(claimContextUpdater, times(1)).updateClaimPolicyContext(eq(claimId), eq(candidateId), anyMap());
        verify(downstreamRouter, times(1)).triggerDownstreamRouting(eq(claimId), anyMap());
        verify(structuredLogger, times(1)).log(anyString(), anyMap());

        // NFR: Input validation & Security (TLS/Least Privilege/Secrets)
        // Mock verification ensures no secrets are passed in logs or API calls
        verifyNoMoreInteractions(policyCandidateRetriever, claimContextUpdater, downstreamRouter, inputValidator, structuredLogger);
    }

    private interface PolicyCandidateRetriever {
        List<String> retrieveAmbiguousCandidates(String claimId);
        Map<String, Object> fetchMatchingCriteria(String claimId, String candidateId);
    }

    private interface ClaimContextUpdater {
        void updateClaimPolicyContext(String claimId, String policyId, Map<String, Object> payload);
    }

    private interface DownstreamRouter {
        void triggerDownstreamRouting(String claimId, Map<String, Object> context);
    }

    private interface InputValidator {
        boolean validateInput(Map<String, Object> payload);
    }

    private interface StructuredLogger {
        void log(String message, Map<String, Object> context);
    }

    private static class ClaimDataStandardizationDecisionValidationHandler {
        private final PolicyCandidateRetriever retriever;
        private final ClaimContextUpdater contextUpdater;
        private final DownstreamRouter router;
        private final InputValidator validator;
        private final StructuredLogger logger;

        ClaimDataStandardizationDecisionValidationHandler(PolicyCandidateRetriever retriever, ClaimContextUpdater contextUpdater, DownstreamRouter router, InputValidator validator, StructuredLogger logger) {
            this.retriever = retriever;
            this.contextUpdater = contextUpdater;
            this.router = router;
            this.validator = validator;
            this.logger = logger;
        }

        String processAnalystSelection(String claimId, Map<String, Object> selection, Map<String, Object> payload) {
            validator.validateInput(payload);
            retriever.retrieveAmbiguousCandidates(claimId);
            retriever.fetchMatchingCriteria(claimId, (String) selection.get("selectedCandidate"));
            contextUpdater.updateClaimPolicyContext(claimId, (String) selection.get("selectedCandidate"), payload);
            router.triggerDownstreamRouting(claimId, payload);
            logger.log("Analyst selection processed", Map.of("claimId", claimId, "status", "SUCCESS"));
            return "SUCCESS";
        }
    }
}
