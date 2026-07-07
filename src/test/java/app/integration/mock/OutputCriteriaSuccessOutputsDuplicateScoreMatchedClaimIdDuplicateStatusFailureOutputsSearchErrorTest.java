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

/**
 * JUnit 5 mock test class for Claim Initiation & Routing: orchestration decision.
 * Verifies output criteria, status updates, emitted events, and user-visible outputs.
 */
@ExtendWith(MockitoExtension.class)
public class OutputCriteriaSuccessDuplicateScoreMatchedClaimIdTest {

    @Mock
    private DuplicateCheckService duplicateCheckService;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private ClaimStatusUpdater claimStatusUpdater;
    @Mock
    private UserNotificationService userNotificationService;

    private OrchestrationDecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        decisionEngine = new OrchestrationDecisionEngine(
                duplicateCheckService, eventPublisher, claimStatusUpdater, userNotificationService);
    }

    @Test
    void testHighMatchStatusUpdateAndDuplicateDetectedEvent() {
        Map<String, Object> mockResult = Map.of(
                "duplicate_score", 0.95,
                "matched_claim_id", "EXISTING-CLAIM-HIGH",
                "duplicate_status", "HIGH_MATCH"
        );
        when(duplicateCheckService.evaluate(any())).thenReturn(mockResult);

        Map<String, Object> result = decisionEngine.processDecision("CLAIM-001", Map.of("payload", "data"));

        assertEquals("HIGH_MATCH", result.get("duplicate_status"));
        assertEquals(0.95, result.get("duplicate_score"));
        assertEquals("EXISTING-CLAIM-HIGH", result.get("matched_claim_id"));

        verify(claimStatusUpdater).update(eq("CLAIM-001"), eq("DUPLICATE_CHECK"), eq("HIGH_MATCH"));
        verify(eventPublisher).emit(eq("duplicate.detected"), any());
        verify(userNotificationService).notify(eq("CLAIM-001"), eq("Notification of potential duplicate."));
        verify(userNotificationService).notify(eq("CLAIM-001"), eq("Link to existing claim."));
    }

    @Test
    void testMediumMatchStatusUpdateAndDuplicateDetectedEvent() {
        Map<String, Object> mockResult = Map.of(
                "duplicate_score", 0.75,
                "matched_claim_id", "EXISTING-CLAIM-MED",
                "duplicate_status", "MEDIUM_MATCH"
        );
        when(duplicateCheckService.evaluate(any())).thenReturn(mockResult);

        Map<String, Object> result = decisionEngine.processDecision("CLAIM-002", Map.of("payload", "data"));

        assertEquals("MEDIUM_MATCH", result.get("duplicate_status"));
        verify(claimStatusUpdater).update(eq("CLAIM-002"), eq("DUPLICATE_CHECK"), eq("MEDIUM_MATCH"));
        verify(eventPublisher).emit(eq("duplicate.detected"), any());
        verify(userNotificationService).notify(eq("CLAIM-002"), eq("Notification of potential duplicate."));
        verify(userNotificationService).notify(eq("CLAIM-002"), eq("Link to existing claim."));
    }

    @Test
    void testNoMatchStatusUpdateAndDuplicateNoMatchEvent() {
        Map<String, Object> mockResult = Map.of(
                "duplicate_score", 0.10,
                "matched_claim_id", null,
                "duplicate_status", "NO_MATCH"
        );
        when(duplicateCheckService.evaluate(any())).thenReturn(mockResult);

        Map<String, Object> result = decisionEngine.processDecision("CLAIM-003", Map.of("payload", "data"));

        assertEquals("NO_MATCH", result.get("duplicate_status"));
        verify(claimStatusUpdater).update(eq("CLAIM-003"), eq("DUPLICATE_CHECK"), eq("NO_MATCH"));
        verify(eventPublisher).emit(eq("duplicate.no_match"), any());
        verify(userNotificationService, never()).notify(any(), anyString());
    }

    @Test
    void testSearchErrorFailureOutput() {
        when(duplicateCheckService.evaluate(any())).thenThrow(new RuntimeException("Search service unavailable"));

        Map<String, Object> result = decisionEngine.processDecision("CLAIM-004", Map.of("payload", "data"));

        assertEquals("search_error", result.get("failure_outputs"));
        verify(eventPublisher).emit(eq("search_error"), any());
        verifyNoInteractions(claimStatusUpdater, userNotificationService);
    }

    // Minimal interfaces to support compilation and mocking
    interface DuplicateCheckService {
        Map<String, Object> evaluate(Map<String, Object> input);
    }

    interface EventPublisher {
        void emit(String eventType, Map<String, Object> payload);
    }

    interface ClaimStatusUpdater {
        void update(String claimId, String statusKey, String statusValue);
    }

    interface UserNotificationService {
        void notify(String claimId, String message);
    }

    static class OrchestrationDecisionEngine {
        private final DuplicateCheckService duplicateCheckService;
        private final EventPublisher eventPublisher;
        private final ClaimStatusUpdater claimStatusUpdater;
        private final UserNotificationService userNotificationService;

        OrchestrationDecisionEngine(DuplicateCheckService duplicateCheckService,
                                    EventPublisher eventPublisher,
                                    ClaimStatusUpdater claimStatusUpdater,
                                    UserNotificationService userNotificationService) {
            this.duplicateCheckService = duplicateCheckService;
            this.eventPublisher = eventPublisher;
            this.claimStatusUpdater = claimStatusUpdater;
            this.userNotificationService = userNotificationService;
        }

        Map<String, Object> processDecision(String claimId, Map<String, Object> payload) {
            Map<String, Object> result = new java.util.HashMap<>();
            try {
                Map<String, Object> evaluation = duplicateCheckService.evaluate(payload);
                result.putAll(evaluation);
                String status = (String) evaluation.get("duplicate_status");
                claimStatusUpdater.update(claimId, "DUPLICATE_CHECK", status);

                if ("NO_MATCH".equals(status)) {
                    eventPublisher.emit("duplicate.no_match", evaluation);
                } else if ("HIGH_MATCH".equals(status) || "MEDIUM_MATCH".equals(status)) {
                    eventPublisher.emit("duplicate.detected", evaluation);
                    userNotificationService.notify(claimId, "Notification of potential duplicate.");
                    userNotificationService.notify(claimId, "Link to existing claim.");
                }
            } catch (Exception e) {
                result.put("failure_outputs", "search_error");
                eventPublisher.emit("search_error", Map.of("error", e.getMessage()));
            }
            return result;
        }
    }
}
