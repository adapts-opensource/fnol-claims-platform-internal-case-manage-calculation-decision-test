package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration mock test for Multi-Channel FNOL Submission:orchestration:validation.
 * Verifies policy match resolution routing through manual review and system assistance.
 * NFR Compliance: thread-safe (JUnit5+Mockito isolation), structured logging, input validation.
 */
@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionOrchestrationValidationTest {

    private static final Logger log = LoggerFactory.getLogger(MultiChannelFnolSubmissionOrchestrationValidationTest.class);

    @Mock
    private PolicyMatchingService policyMatchingService;

    @Mock
    private ManualReviewService manualReviewService;

    @Mock
    private StateTransitionService stateTransitionService;

    private FnolOrchestrationValidator orchestrationValidator;

    @BeforeEach
    void setUp() {
        orchestrationValidator = new FnolOrchestrationValidator(
                policyMatchingService,
                manualReviewService,
                stateTransitionService
        );
    }

    @Test
    void purpose_resolve_multiple_or_unmatched_policy_matches_through_manual_review_and_system_assistance() {
        // Arrange: Initial submission with unmatched/multiple policy matches
        String submissionId = UUID.randomUUID().toString();
        Map<String, Object> initialPayload = Map.of(
                "claimType", "AUTO",
                "channel", "WEB",
                "policySearchCriteria", Map.of("vin", "1HGCM82633A004352")
        );

        // Simulate 0 or >1 matches from policy matching service
        when(policyMatchingService.resolvePolicyMatches(anyString(), anyMap()))
                .thenReturn(List.of()); // Unmatched scenario
        when(stateTransitionService.transitionTo(eq(submissionId), eq("MANUAL_REVIEW"), anyMap()))
                .thenReturn(Map.of(
                        "id", submissionId,
                        "state", "MANUAL_REVIEW",
                        "payload", Map.of("reviewStatus", "PENDING", "matchCount", 0)
                ));

        log.info("Starting FNOL validation for submission: {}", submissionId);

        // Act: Initial submission triggers validation and routes to manual review
        Map<String, Object> reviewState = orchestrationValidator.validateAndTransition(submissionId, initialPayload);

        // Assert: Verify manual review routing and payload structure
        assertNotNull(reviewState);
        assertEquals("MANUAL_REVIEW", reviewState.get("state"));
        assertEquals("PENDING", reviewState.get("payload").get("reviewStatus"));
        verify(stateTransitionService).transitionTo(eq(submissionId), eq("MANUAL_REVIEW"), anyMap());
        verify(policyMatchingService).resolvePolicyMatches(eq(submissionId), anyMap());

        // Arrange: System assistance resolves matches during manual review
        Map<String, Object> resolvedPayload = Map.of(
                "id", submissionId,
                "state", "RESOLVED",
                "payload", Map.of("reviewStatus", "CLOSED", "matchedPolicyId", "POL-98765", "confidenceScore", 0.98)
        );

        when(policyMatchingService.resolvePolicyMatches(eq(submissionId), anyMap()))
                .thenReturn(List.of("POL-98765")); // Single resolved match
        when(stateTransitionService.transitionTo(eq(submissionId), eq("RESOLVED"), anyMap()))
                .thenReturn(resolvedPayload);

        // Act: Process manual review resolution with system assistance
        Map<String, Object> finalState = orchestrationValidator.processManualReviewResolution(submissionId, reviewState);

        // Assert: Verify resolved state, payload updates, and infra I/O contract compliance
        assertNotNull(finalState);
        assertEquals("RESOLVED", finalState.get("state"));
        assertEquals("POL-98765", finalState.get("payload").get("matchedPolicyId"));
        assertEquals("CLOSED", finalState.get("payload").get("reviewStatus"));
        verify(stateTransitionService).transitionTo(eq(submissionId), eq("RESOLVED"), anyMap());
        verify(policyMatchingService, times(2)).resolvePolicyMatches(eq(submissionId), anyMap());

        log.info("Successfully resolved multiple/unmatched policy matches for submission: {}", submissionId);
    }

    // Stubbed interfaces/services for orchestration validation layer
    interface PolicyMatchingService {
        List<String> resolvePolicyMatches(String submissionId, Map<String, Object> criteria);
    }

    interface ManualReviewService {
        Map<String, Object> submitForReview(String submissionId, Map<String, Object> payload);
    }

    interface StateTransitionService {
        Map<String, Object> transitionTo(String id, String newState, Map<String, Object> payload);
    }

    // Service under test
    static class FnolOrchestrationValidator {
        private final PolicyMatchingService policyMatchingService;
        private final ManualReviewService manualReviewService;
        private final StateTransitionService stateTransitionService;

        FnolOrchestrationValidator(PolicyMatchingService policyMatchingService,
                                   ManualReviewService manualReviewService,
                                   StateTransitionService stateTransitionService) {
            this.policyMatchingService = policyMatchingService;
            this.manualReviewService = manualReviewService;
            this.stateTransitionService = stateTransitionService;
        }

        Map<String, Object> validateAndTransition(String id, Map<String, Object> payload) {
            // Input validation (NFR)
            if (id == null || id.isBlank() || payload == null) {
                throw new IllegalArgumentException("Submission ID and payload must not be null or empty");
            }
            List<String> matches = policyMatchingService.resolvePolicyMatches(id, payload);
            if (matches.size() != 1) {
                return stateTransitionService.transitionTo(id, "MANUAL_REVIEW", payload);
            }
            return stateTransitionService.transitionTo(id, "VALIDATED", payload);
        }

        Map<String, Object> processManualReviewResolution(String id, Map<String, Object> currentState) {
            Map<String, Object> payload = (Map<String, Object>) currentState.getOrDefault("payload", Map.of());
            List<String> resolvedMatches = policyMatchingService.resolvePolicyMatches(id, payload);
            Map<String, Object> updatedPayload = Map.of("reviewStatus", "CLOSED", "matchedPolicyId", resolvedMatches.get(0));
            return stateTransitionService.transitionTo(id, "RESOLVED", updatedPayload);
        }
    }
}
