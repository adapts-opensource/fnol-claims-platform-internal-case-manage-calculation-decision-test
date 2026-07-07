package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StateTransitionAuditAndExplainabilityMockTest {

    @Mock
    private StateTransitionService stateTransitionService;

    @Mock
    private AuditTrailRepository auditTrailRepository;

    @Mock
    private ExplainabilityEngine explainabilityEngine;

    private String claimId;
    private String previousState;
    private String newState;

    @BeforeEach
    void setUp() {
        claimId = UUID.randomUUID().toString();
        previousState = "PENDING_REVIEW";
        newState = "APPROVED";
    }

    @Test
    void full_audit_trail_and_explainability_output_generated() {
        // Arrange
        Map<String, Object> transitionContext = Map.of(
            "claimId", claimId,
            "previousState", previousState,
            "newState", newState,
            "triggeredBy", "SYSTEM_AUTO_DECISION",
            "timestamp", LocalDateTime.now()
        );

        Map<String, Object> expectedExplainability = Map.of(
            "reasonCode", "AUTO_APPROVAL_THRESHOLD_MET",
            "confidenceScore", 0.95,
            "factors", List.of("CLAIM_AMOUNT_WITHIN_LIMIT", "INSURED_HISTORY_CLEAN")
        );

        when(explainabilityEngine.generateExplainabilityOutput(eq(claimId), eq(newState), anyMap()))
                .thenReturn(expectedExplainability);

        // Act
        Map<String, Object> result = stateTransitionService.transitionState(transitionContext);

        // Assert Audit Trail
        ArgumentCaptor<Map<String, Object>> auditCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditTrailRepository, times(1)).save(auditCaptor.capture());
        Map<String, Object> savedAudit = auditCaptor.getValue();
        assertEquals(claimId, savedAudit.get("claimId"));
        assertEquals(previousState, savedAudit.get("previousState"));
        assertEquals(newState, savedAudit.get("newState"));
        assertNotNull(savedAudit.get("auditTimestamp"));
        assertFalse(((List<?>) savedAudit.get("auditEntries")).isEmpty());

        // Assert Explainability Output
        verify(explainabilityEngine, times(1)).generateExplainabilityOutput(eq(claimId), eq(newState), anyMap());
        assertNotNull(result.get("explainabilityOutput"));
        assertEquals(expectedExplainability, result.get("explainabilityOutput"));

        // Verify no unexpected interactions
        verifyNoMoreInteractions(auditTrailRepository, explainabilityEngine);
    }

    static interface StateTransitionService {
        Map<String, Object> transitionState(Map<String, Object> context);
    }

    static interface AuditTrailRepository {
        void save(Map<String, Object> auditTrail);
    }

    static interface ExplainabilityEngine {
        Map<String, Object> generateExplainabilityOutput(String claimId, String newState, Map<String, Object> context);
    }
}
