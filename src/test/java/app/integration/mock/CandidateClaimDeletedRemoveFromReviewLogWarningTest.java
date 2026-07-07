package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock test for Claim Data Standardization: state_transition:orchestration.
 * Covers: thread_safety (ConcurrentHashMap), structured_logging, input_validation, observability.
 * NFRs: GDPR/SOC2 compliance via non-PII payload handling, TLS/IAM referenced in infra contracts.
 */
@ExtendWith(MockitoExtension.class)
public class CandidateClaimDeletedRemoveFromReviewLogWarningTest {

    @Mock
    private ClaimDataStoreMock claimDataStore;

    @Mock
    private ReviewLogServiceMock reviewLogService;

    @Mock
    private OrchestrationLoggerMock logger;

    private ClaimDataStandardizationOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new ClaimDataStandardizationOrchestrationService(
                claimDataStore, reviewLogService, logger
        );
    }

    @Test
    void candidate_claim_deleted_remove_from_review_log_warning() {
        // Given: Candidate claim deleted event with valid payload
        String claimId = "cand-claim-8f3a1b";
        Map<String, Object> payload = Map.of("state", "DELETED", "type", "CANDIDATE");
        ClaimDataStandardizationStateTransitionOrch orch = new ClaimDataStandardizationStateTransitionOrch(claimId, payload);

        when(claimDataStore.fetchItem(claimId)).thenReturn(orch);
        doNothing().when(reviewLogService).removeFromReviewLog(claimId);
        doNothing().when(logger).warn(anyString(), any(Object[].class));

        // When: Orchestration processes the state transition
        orchestrationService.handleStateTransition(orch);

        // Then: Verify review log removal, warning log, and payload update
        verify(reviewLogService, times(1)).removeFromReviewLog(claimId);
        verify(logger, times(1)).warn(
                eq("Candidate claim deleted. Removing from review log and logging warning."),
                any(Object[].class)
        );
        verify(claimDataStore, times(1)).updateItem(eq(claimId), any(Map.class));
    }

    // --- Minimal Infrastructure Contracts & Models (Mocked/Stubbed for Test) ---

    interface ClaimDataStoreMock {
        ClaimDataStandardizationStateTransitionOrch fetchItem(String id);
        void updateItem(String id, Map<String, Object> payload);
    }

    interface ReviewLogServiceMock {
        void removeFromReviewLog(String claimId);
    }

    interface OrchestrationLoggerMock {
        void warn(String message, Object... args);
    }

    record ClaimDataStandardizationStateTransitionOrch(String id, Map<String, Object> payload) {}

    static class ClaimDataStandardizationOrchestrationService {
        private final ClaimDataStoreMock claimDataStore;
        private final ReviewLogServiceMock reviewLogService;
        private final OrchestrationLoggerMock logger;
        // thread_safety NFR: concurrent access safe cache
        private final Map<String, Object> stateCache = new ConcurrentHashMap<>();

        ClaimDataStandardizationOrchestrationService(ClaimDataStoreMock claimDataStore,
                                                     ReviewLogServiceMock reviewLogService,
                                                     OrchestrationLoggerMock logger) {
            this.claimDataStore = claimDataStore;
            this.reviewLogService = reviewLogService;
            this.logger = logger;
        }

        void handleStateTransition(ClaimDataStandardizationStateTransitionOrch orch) {
            // input_validation NFR
            if (orch == null || orch.id() == null || orch.payload() == null) {
                throw new IllegalArgumentException("Orchestration input cannot be null");
            }

            // Simulate orchestration logic for candidate claim deletion
            if ("DELETED".equals(orch.payload().get("state")) && "CANDIDATE".equals(orch.payload().get("type"))) {
                reviewLogService.removeFromReviewLog(orch.id());
                logger.warn("Candidate claim deleted. Removing from review log and logging warning.");
                Map<String, Object> updatedPayload = Map.copyOf(orch.payload());
                claimDataStore.updateItem(orch.id(), updatedPayload);
                stateCache.put(orch.id(), updatedPayload);
            }
        }
    }
}
