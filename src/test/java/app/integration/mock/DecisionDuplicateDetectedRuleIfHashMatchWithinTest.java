package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FnolDecisionDuplicateDetectionTest {

    @Mock
    private HashLookupService hashLookupService;
    @Mock
    private AuditLogger auditLogger;
    @Mock
    private RoutingService routingService;

    private FnolDecisionService fnolDecisionService;

    @BeforeEach
    void setUp() {
        fnolDecisionService = new FnolDecisionService(hashLookupService, auditLogger, routingService);
    }

    @Test
    void decision_duplicate_detected_rule_if_hash_match_within_window_reject_duplicate_else_proceed_expected_outcome_deduplication_log_and_routing_decision() {
        // Arrange
        String claimId = "CLM-98765";
        String submissionHash = "sha256:abc123def456";
        int windowHours = 24;
        boolean hashMatchWithinWindow = true;

        when(hashLookupService.checkMatchWithinWindow(eq(claimId), eq(submissionHash), eq(windowHours)))
                .thenReturn(hashMatchWithinWindow);

        // Act
        DecisionOutcome outcome = fnolDecisionService.evaluate(claimId, submissionHash, windowHours);

        // Assert
        assertEquals(DecisionOutcome.REJECT_DUPLICATE, outcome, "Expected REJECT_DUPLICATE when hash matches within window");

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogger, times(1)).log(eq("fnol.decision.dedup"), logCaptor.capture(), any());
        String loggedMessage = logCaptor.getValue();
        assertTrue(loggedMessage.contains("Duplicate detected"), "Deduplication log must record duplicate detection");

        verify(routingService, times(1)).route(eq(claimId), eq(RoutingDestination.DEDUPLICATION_QUEUE), any());
    }
}
